package org.rap.poccodingplatform.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.rap.poccodingplatform.dto.ErrorDetail;
import org.rap.poccodingplatform.dto.ExecutionResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DockerSandboxService {
    private static final String JDK_IMAGE = "secure-java-sandbox";
    private static final Pattern ERROR_PATTERN = Pattern.compile("Main\\.java:(\\d+):\\s+(error|warning):\\s+(.*)");

    private DockerClient getDockerClient(){
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config,httpClient);
    }

    public ExecutionResult runCode(String userCode){
        DockerClient dockerClient = getDockerClient();

        String containerId = null;

        StringBuilder stdoutLog = new StringBuilder();
        StringBuilder stderrLog = new StringBuilder();

        long startTime = System.currentTimeMillis();

        try{
            // Cấu hình Docker Container
            // Mount thư mục tạm từ Host vào /app trong Container
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNetworkMode("none")
                    .withMemory(256 * 1024 * 1024L)
                    .withMemorySwap(256 * 1024 * 1024L)
                    .withCpuQuota(50000L)
                    .withPidsLimit(50L)
                    .withReadonlyRootfs(true)
                    .withTmpFs(Collections.singletonMap("/app", "rw,size=10m,mode=1777"));

            // Tạo Container nhưng chưa start
            CreateContainerResponse container = dockerClient.createContainerCmd(JDK_IMAGE)
                    .withWorkingDir("/app")
                    .withHostConfig(hostConfig)
                    .withCmd("sleep", "100") // Container sẽ sống 60s rồi tự chết (đủ để ta chạy code)
                    .exec();

            containerId = container.getId();

            dockerClient.startContainerCmd(containerId).exec();

            ExecCreateCmdResponse writeCodeCmd = dockerClient.execCreateCmd(containerId)
                    .withAttachStdin(true) // QUAN TRỌNG: Cho phép nhập liệu
                    .withCmd("sh", "-c", "cat > Main.java")
                    .exec();

            // B3.2: Thực thi lệnh và bơm userCode vào luồng input
            dockerClient.execStartCmd(writeCodeCmd.getId())
                    .withStdIn(new ByteArrayInputStream(userCode.getBytes(StandardCharsets.UTF_8)))
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>())
                    .awaitCompletion(5, TimeUnit.SECONDS);

            ExecCreateCmdResponse runCmd = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("sh", "-c", "javac Main.java && java Main")
                    .exec();

            dockerClient.execStartCmd(runCmd.getId())
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame item) {
                            String payload = new String(item.getPayload(), StandardCharsets.UTF_8);
                            if (item.getStreamType().name().equals("STDERR")) {
                                stderrLog.append(payload);
                            } else {
                                stdoutLog.append(payload);
                            }
                        }
                    }).awaitCompletion(10, TimeUnit.SECONDS); // Tăng timeout lên chút cho an toàn

            // Lấy Exit Code CHUẨN từ Docker (0 = Success, !=0 = Error)
            InspectExecResponse execResponse = dockerClient.inspectExecCmd(runCmd.getId()).exec();
            long exitCode = execResponse.getExitCodeLong();

            // Lọc bỏ thông báo rác của JVM trong stderr
            String cleanStderr = stderrLog.toString()
                    .replace("Picked up JAVA_TOOL_OPTIONS: -Xmx256m\n", "") // Xóa dòng này
                    .replace("Picked up JAVA_TOOL_OPTIONS: -Xmx256m", "")   // Xóa biến thể không xuống dòng
                    .trim();

            long timeTaken = System.currentTimeMillis() - startTime;

            List<ErrorDetail> parsedErrors = parseErrorLog(cleanStderr);

            return new ExecutionResult(stdoutLog.toString(), cleanStderr, timeTaken, exitCode, parsedErrors);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ExecutionResult("", "Error: " + ex.getMessage(), 0, -1,null);
        }
    }

    private List<ErrorDetail> parseErrorLog(String rawLog) {
        List<ErrorDetail> details = new ArrayList<>();
        if (rawLog == null || rawLog.isEmpty()) return details;

        // Tách log thành từng dòng
        String[] lines = rawLog.split("\n");

        for (String line : lines) {
            Matcher matcher = ERROR_PATTERN.matcher(line);
            if (matcher.find()) {
                try {
                    // Group 1: Line number
                    int lineNumber = Integer.parseInt(matcher.group(1));
                    // Group 2: Type (error/warning)
                    String type = matcher.group(2);
                    // Group 3: Message
                    String message = matcher.group(3);

                    details.add(new ErrorDetail(lineNumber, type, message));
                } catch (Exception e) {
                    // Nếu parse lỗi dòng này thì bỏ qua, không làm crash app
                }
            }
        }
        return details;
    }
}
