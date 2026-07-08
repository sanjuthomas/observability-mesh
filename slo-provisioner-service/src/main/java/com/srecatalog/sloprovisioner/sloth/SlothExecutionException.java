package com.srecatalog.sloprovisioner.sloth;

public class SlothExecutionException extends RuntimeException {

    public SlothExecutionException(String message) {
        super(message);
    }

    public SlothExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
