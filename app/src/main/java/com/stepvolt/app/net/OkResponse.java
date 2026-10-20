package com.stepvolt.app.net;

public class OkResponse {
    public boolean ok;
    /** Só vêm ao trocar a senha: o servidor encerra as outras sessões e entrega um par novo. */
    public String token;
    public String refreshToken;
}
