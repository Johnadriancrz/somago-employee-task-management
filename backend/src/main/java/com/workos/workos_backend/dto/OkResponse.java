package com.workos.workos_backend.dto;

/** {@code { ok: true }} — the shape used by every delete-style endpoint (BACKEND.md). */
public record OkResponse(boolean ok) {

    public static final OkResponse OK = new OkResponse(true);
}
