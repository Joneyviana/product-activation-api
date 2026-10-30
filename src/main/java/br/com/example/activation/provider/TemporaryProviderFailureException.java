package br.com.example.activation.provider;

/** Indica uma falha transitória do provedor que pode ser tentada novamente. */
public class TemporaryProviderFailureException extends RuntimeException {
    public TemporaryProviderFailureException(String message) {
        super(message);
    }
}
