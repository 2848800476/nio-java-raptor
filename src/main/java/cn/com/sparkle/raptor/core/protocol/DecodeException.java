package cn.com.sparkle.raptor.core.protocol;

import java.io.IOException;

public class DecodeException extends IOException {

	private static final long serialVersionUID = 1L;

	public DecodeException() {
		super();
	}

	// public DecodeException(String message, Throwable cause,
	// boolean enableSuppression, boolean writableStackTrace) {
	// super(message, cause, enableSuppression, writableStackTrace);
	// }

	public DecodeException(String message, Throwable cause) {
		super(message, cause);
	}

	public DecodeException(String message) {
		super(message);
	}

	public DecodeException(Throwable cause) {
		super(cause);
	}

}
