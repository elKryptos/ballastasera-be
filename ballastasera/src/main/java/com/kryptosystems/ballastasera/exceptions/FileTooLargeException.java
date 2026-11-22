package com.kryptosystems.ballastasera.exceptions;

/** 413: el archivo pasa el limite propio del endpoint (ej. 5 MB la foto de perfil). */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
