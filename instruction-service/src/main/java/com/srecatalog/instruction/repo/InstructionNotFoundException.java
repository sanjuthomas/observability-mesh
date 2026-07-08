package com.srecatalog.instruction.repo;

public class InstructionNotFoundException extends RuntimeException {

    public InstructionNotFoundException(String instructionId) {
        super("instruction not found: " + instructionId);
    }
}
