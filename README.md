# Distributed Rate Limiter

A distributed rate limiter implementation that batches local request counts before updating a shared counter store.

This project explores approaches to reducing write pressure on distributed counters by accumulating requests in memory and periodically flushing aggregated deltas.

---

## Overview

Instead of incrementing a shared store on every request, this implementation:

- Maintains per-key request counts locally  
- Flushes accumulated deltas to a distributed backend  
- Applies a fixed-window rate limiting model  
- Uses atomic primitives for lightweight concurrency handling  

Distributed persistence is abstracted behind the interface:

```java
CompletableFuture<Integer> incrementByAndExpire(
    String key,
    int delta,
    int expirationSeconds
);
```

The design separates local state management from distributed storage concerns, allowing different backend implementations.

---

## Design Intent

The implementation focuses on:

- Reducing unnecessary distributed writes  
- Isolating per-key state  
- Keeping concurrency coordination explicit and contained  
- Maintaining a predictable fixed-window structure  

---