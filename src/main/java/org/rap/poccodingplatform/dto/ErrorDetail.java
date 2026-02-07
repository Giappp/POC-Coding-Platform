package org.rap.poccodingplatform.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorDetail {
    private int line;
    private String type;
    private String message;

    public ErrorDetail(int line, String type, String message) {
        this.line = line;
        this.type = type;
        this.message = message;
    }
}
