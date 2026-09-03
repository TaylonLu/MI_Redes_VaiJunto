package org.UEFS.vaijunto.Domain.Interfaces;

public interface BaseMapper <T extends Identificavel> {
    String toString(T dado);
    T fromString(String dados);
    Class<T> getType();
}
