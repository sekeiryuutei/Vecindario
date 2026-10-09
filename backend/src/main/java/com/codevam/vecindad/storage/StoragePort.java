package com.codevam.vecindad.storage;

public interface StoragePort {
    String store(String path, byte[] content, String contentType);
    byte[] load(String path);
    void delete(String path);
    boolean exists(String path);
}

