package com.beeleza.pixflow.application.port.out;

import java.util.function.Supplier;

/**
 * Runs work inside a database transaction that is already committed when {@link #run} returns
 */
public interface TransactionRunner {

    <T> T run(Supplier<T> work);
}
