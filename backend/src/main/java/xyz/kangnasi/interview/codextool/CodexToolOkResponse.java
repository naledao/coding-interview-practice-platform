package xyz.kangnasi.interview.codextool;

public record CodexToolOkResponse(boolean ok) {

    public static CodexToolOkResponse success() {
        return new CodexToolOkResponse(true);
    }
}
