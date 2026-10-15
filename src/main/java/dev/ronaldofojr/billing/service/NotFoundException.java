package dev.ronaldofojr.billing.service;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " nao encontrado(a)");
    }
}
