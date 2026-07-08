package com.srecatalog.instruction.model;

public final class InstructionConstants {

    public static final String CURRENT_OUT = "9999-12-31T23:59:59Z";

    private InstructionConstants() {
    }

    public static String documentKey(String instructionId, int versionNumber) {
        return instructionId + "|" + versionNumber;
    }

    public static String instructionIdFromDocumentKey(String documentKey) {
        int idx = documentKey.lastIndexOf('|');
        return idx < 0 ? documentKey : documentKey.substring(0, idx);
    }
}
