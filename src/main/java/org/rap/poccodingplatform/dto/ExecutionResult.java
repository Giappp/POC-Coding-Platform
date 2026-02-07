package org.rap.poccodingplatform.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExecutionResult
{
    private String stdout;
    private String stderr;      // Giữ lại bản raw phòng khi cần debug
    private long timeTaken;
    private long exitCode;
    private List<ErrorDetail> errorDetails;
}
