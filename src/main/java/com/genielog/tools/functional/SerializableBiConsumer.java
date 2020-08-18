package com.genielog.tools.functional;

import java.io.Serializable;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface SerializableBiConsumer<T,V> extends Serializable, BiConsumer<T,V> {

}
