package org.UEFS.Server.controller.relogio;

public record CMessage(MessageType tipo, String remetente, long timeStamp)
{
    public String json() {
        return String.format("""
                {"tipo": "%s", "remetente": "%s", "timeStamp": "%d"}""",
                tipo.name(), remetente, timeStamp);
    }
}
