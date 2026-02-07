# POC Coding Platform

Bản Backend demo cho phép biên dịch và chạy mã nguồn sử dụng Docker làm sandbox để thực thi code

Mục đích:
- Cung cấp môi trường cô lập để thực thi mã không tin cậy (ví dụ: bài tập lập trình, bài kiểm tra tự động) bằng Docker.
- Trình bày cách dựng một sandbox đơn giản cho Java (javac/java) cùng với các giới hạn tài nguyên cơ bản.

Tính năng chính:
- Mỗi submission được chạy trong một container Docker riêng biệt.
- Giới hạn tài nguyên: bộ nhớ, CPU, số tiến trình (pids), filesystem read-only, và mount `tmpfs` cho thư mục làm việc.
- Mạng bị vô hiệu hoá trong container (`--network=none`).
- Thời gian chạy có timeout để tránh việc chiếm tài nguyên lâu dài.
- Kết quả stdout/stderr được thu thập và trả về cho caller; lỗi biên dịch được phân tích thành cấu trúc có `file`, `line`, `message`.

Yêu cầu môi trường:
- Java 17+
- Maven
- Docker daemon (ứng dụng phải có quyền truy cập Docker để tạo container)

Cách build và chạy ứng dụng:

1. Build bằng Maven:

```bash
mvn clean package -DskipTests
```

2. Chạy ứng dụng:

```bash
mvn spring-boot:run
# hoặc
java -jar target/*.jar
```

Docker sandbox image:
- Ứng dụng giả định tồn tại một image sandbox (ví dụ `secure-java-sandbox`) có JDK và môi trường tối thiểu cho `javac`/`java`.
- Ví dụ build image (tuỳ theo Dockerfile của bạn):

```bash
docker build -t secure-java-sandbox -f Dockerfile .
```

Luồng thực thi tóm tắt:
1. Ứng dụng tạo một container từ image sandbox.
2. Container được cấu hình vô hiệu hoá mạng, giới hạn bộ nhớ/CPU, PID limit, và read-only rootfs; thư mục làm việc được mount lên `tmpfs`.
3. Mã nguồn người dùng (ví dụ `Main.java`) được ghi vào container.
4. Thực hiện: `javac Main.java && java Main` (hoặc lệnh tương tự). Stdout và stderr được thu thập.
5. Nếu có lỗi biên dịch, stderr được phân tích thành cấu trúc lỗi chi tiết.

Lưu ý bảo mật:
- Docker giúp cô lập nhiều mặt nhưng không tuyệt đối; cân nhắc thêm user namespaces, seccomp profile, gVisor, hoặc các kỹ thuật isolation mạnh hơn nếu threat model cao.
- Không lưu trữ dữ liệu người dùng vào host mà không xác thực; giới hạn thời gian sống của container.
- Giám sát và log hoạt động container để audit.

Cấu hình và tinh chỉnh:
- Các tham số timeout, bộ nhớ và CPU được đặt trong mã nguồn (`DockerSandboxService`). Tùy môi trường mà điều chỉnh.
- Docker client sử dụng cấu hình mặc định từ môi trường Docker trên host.

---
(c) POC Coding Platform - Generated README (Tiếng Việt) - {Ngày tạo: 07/02/2026}

