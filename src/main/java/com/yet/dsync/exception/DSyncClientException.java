/*
 * Copyright (C) 2016  Yuriy Tkach
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.    
 */

package com.yet.dsync.exception;

public class DSyncClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DSyncClientException(String message) {
        super(message);
    }

    public DSyncClientException(Throwable cause) {
        super(cause);
    }

    public DSyncClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public DSyncClientException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
