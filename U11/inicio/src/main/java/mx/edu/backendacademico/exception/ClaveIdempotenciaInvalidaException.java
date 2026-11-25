package mx.edu.backendacademico.exception;

public class ClaveIdempotenciaInvalidaException extends RuntimeException {
    public ClaveIdempotenciaInvalidaException() {
        super("Idempotency-Key debe tener de 1 a 100 caracteres ASCII visibles");
    }
}
