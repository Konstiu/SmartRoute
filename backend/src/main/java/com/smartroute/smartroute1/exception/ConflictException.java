package com.smartroute.smartroute1.exception;

import java.util.List;

public class ConflictException extends ErrorListException {
    public ConflictException(String messageSummary, List<String> errors) {
        super("Conflicts", messageSummary, errors);
    }

    public ConflictException(String messageSummary) {
        super(messageSummary);
    }
}
