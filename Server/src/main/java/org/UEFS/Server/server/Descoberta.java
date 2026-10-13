package org.UEFS.Server.server;

import org.jetbrains.annotations.NotNull;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Descoberta {
    private static final String GRUPO = "239.255.0.1";
    private static final int PORTA_MULTICAST = 4446;
    private static final long MAX_MS = 10_000;

    private final String meuID;
    private final int minhaPorta;

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    record Peer(String endereco, long visto) {}

    public Descoberta(String meuID, int minhaPorta) {
        this.meuID = meuID;
        this.minhaPorta = minhaPorta;
    }

    public void iniciar() throws Exception {
        InetAddress grupo = InetAddress.getByName(GRUPO);
        InetSocketAddress grupoAddr = new InetSocketAddress(grupo, PORTA_MULTICAST);
        NetworkInterface NI = escolherInterface();

        var escuta = createEscuta(grupoAddr, NI);
        escuta.start();

        ScheduledExecutorService EX = Executors.newSingleThreadScheduledExecutor(R -> {
            Thread T = new Thread();
            T.setDaemon(true);
            return T;
        });

        EX.scheduleAtFixedRate(() -> {
            try (MulticastSocket S = new MulticastSocket()) {
                S.setNetworkInterface(NI);
                byte[] dados = (meuID + ";" + minhaPorta).getBytes(StandardCharsets.UTF_8);
                S.send(new DatagramPacket(dados, dados.length, grupo, PORTA_MULTICAST));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    @NotNull
    private Thread createEscuta(InetSocketAddress grupoAddr, NetworkInterface NI) {
        Thread escuta = new Thread(() -> {
           try (MulticastSocket S = new MulticastSocket(PORTA_MULTICAST)) {
               S.joinGroup(grupoAddr, NI);
               byte[] buffer = new byte[256];
               while (true) {
                   DatagramPacket P = new DatagramPacket(buffer, buffer.length);
                   S.receive(P);
                   String msg = new String(P.getData(), 0, P.getLength(), StandardCharsets.UTF_8);
                   String[] partes = msg.split(";");

                   if (partes.length != 2 || partes[0].equals(meuID)) continue;

                   String endereco = P.getAddress().getHostAddress() + ":" + partes[1];
                   peers.put(partes[0], new Peer(endereco, System.currentTimeMillis()));
               }

           } catch (Exception e) {
               e.printStackTrace();
           }
        });

        escuta.setDaemon(true);
        return escuta;
    }

    public Map<String, String> peersAtivos() {
        long agora = System.currentTimeMillis();
        peers.values().removeIf(P -> agora - P.visto() > MAX_MS);
        Map<String, String> R = new HashMap<>();
        peers.forEach((id, p) -> R.put(id, p.endereco));
        return R;
    }

    private static NetworkInterface escolherInterface() throws SocketException {
        for (NetworkInterface NI : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!NI.isUp() || NI.isLoopback() || NI.isVirtual() || NI.supportsMulticast()) continue;
            boolean temIPv4 = Collections.list(NI.getInetAddresses()).stream()
                    .anyMatch(A -> A instanceof Inet4Address);
            if (temIPv4) return NI;
        }

        throw new SocketException("Nenhuma interface multicast encontrada.");
    }
}
