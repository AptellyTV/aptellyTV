package app.aptelly.tv.install;

import java.io.IOException;

public final class NoMatchingArtifactException extends IOException {
    public final String reasonCode;

    public NoMatchingArtifactException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }
}
