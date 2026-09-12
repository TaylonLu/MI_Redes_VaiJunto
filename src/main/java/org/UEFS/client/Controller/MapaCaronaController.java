package org.UEFS.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.UEFS.client.utils.Resources;
import org.UEFS.client.utils.Toast;
import org.UEFS.vaijunto.Domain.Caronas.Rota;
import org.UEFS.vaijunto.Domain.FileManager;
import org.UEFS.vaijunto.Domain.Interfaces.Cidade;
import org.UEFS.vaijunto.Domain.Interfaces.Trecho;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MapaCaronaController {
    private final List<Cidade> cidades = new ArrayList<>();
    private final List<Trecho> rotaMotorista = new ArrayList<>();
    public DatePicker dataCarona;
    private Cidade cidadeAnterior = null;
    private double anchorX;
    private double anchorY;

    @FXML private Button btnCancelar;
    @FXML private Button btnRecomecar;
    @FXML private Pane containerMapa;
    @FXML private Label lblStatus;
    @FXML private ListView<String> listViewPercurso;
    @FXML private Group mapaGrupo;
    @FXML private VBox painelPopup;
    @FXML private StackPane stackContainer;
    @FXML private TextField txtComentarios;
    @FXML private TextField txtDataPartida;
    @FXML private TextField txtVagas;

    @FXML
    void cancelarRota(ActionEvent event) {
        painelPopup.setVisible(false);
    }

    @FXML
    void confirmarRota(ActionEvent event) {
        if (rotaMotorista.isEmpty()) {
            Toast.warning("Selecione um trecho antes de terminar.");
            return;
        }

        painelPopup.setVisible(true);
    }

    @FXML
    void enviarCaronaServidor(ActionEvent event) {
        try {
            int vagas = Integer.parseInt(txtVagas.getText().trim());
            if (vagas <= 0) throw new IllegalArgumentException();
            String dataStr = txtDataPartida.getText().trim();

            Rota rotaFinal = new Rota(rotaMotorista);

            Toast.success("Carona publicada com sucesso!");
            painelPopup.setVisible(false);
        } catch (NumberFormatException e) {
            Toast.error("Numero de vagas deve ser um valor inteiro válido.");
        } catch (IllegalArgumentException e) {
            Toast.error("O número de vagas deve ser de pelo menos 1.");
        }
    }

    @FXML
    void fecharPopup(ActionEvent event) {
        painelPopup.setVisible(false);
    }

    @FXML
    void recomecarRota(ActionEvent event) {
        rotaMotorista.clear();
        listViewPercurso.getItems().clear();
        cidadeAnterior = null;

        mapaGrupo.getChildren().clear();
        carregarImagemFundo();
        renderizarMapa();

        painelPopup.setVisible(false);
        Toast.info("Rota reiniciada.");
    }

    @FXML
    public void initialize() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(containerMapa.widthProperty());
        clip.heightProperty().bind(containerMapa.heightProperty());
        containerMapa.setClip(clip);

        carregarCidades();
        carregarImagemFundo();
        renderizarMapa();
    }

    public void carregarCidades() {
        try {
            this.cidades.addAll(Objects.requireNonNull(FileManager.carregarDeJson("data/cidades.json")));
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    private void carregarImagemFundo() {
        try {
            InputStream resource = Resources.getStream("/images/mapa_kalos.jpg");
            ImageView imgFundo = new ImageView(new Image(resource));
            mapaGrupo.getChildren().add(imgFundo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderizarMapa() {
        for (Cidade C1 : cidades) {
            for (int idVizinho : C1.estradas) {
                Cidade C2 = cidades.stream()
                        .filter(C -> C.id == idVizinho)
                        .findFirst()
                        .orElse(null);
                if (C2 == null || C1.id > C2.id) continue;

                Line linha = new Line(C1.x, C1.y, C2.x, C2.y);
                linha.getStyleClass().add("estrada-linha");
                mapaGrupo.getChildren().add(linha);
            }

            Circle circulo = new Circle(C1.x, C1.y, 10);
            circulo.getStyleClass().add("cidade-no");
            circulo.setUserData(C1);

            Text texto = new Text(C1.x + 15, C1.y + 5, C1.nome);
            texto.setFont(Font.font("System", FontWeight.BOLD, 12));
            texto.getStyleClass().add("cidade-texto");
            texto.visibleProperty().bind(circulo.hoverProperty());

            circulo.setOnMouseClicked(_ -> handleCidadeClick(C1, circulo));

            mapaGrupo.getChildren().addAll(circulo, texto);
        }
    }

    private void handleCidadeClick(Cidade cidadeClicada, Circle circulo) {
        if (cidadeAnterior == null) {
            cidadeAnterior = cidadeClicada;
            circulo.getStyleClass().add("cidade-selecionada");
            lblStatus.setText("Origem da carona definida: " + cidadeClicada.nome);

            // Adiciona a primeira cidade na lista visual
            listViewPercurso.getItems().add(cidadeClicada.nome);
        } else {
            boolean saoAdjacentes = cidadeAnterior.estradas.contains(cidadeClicada.id);

            if (!saoAdjacentes) {
                Toast.error("Não há estrada lidando as Cidades diretamente.");
                lblStatus.setText("❌ Movimento inválido! Não há estrada direta entre " + cidadeAnterior.nome + " e " + cidadeClicada.nome);
                return;
            }

            rotaMotorista.add(new Trecho(cidadeAnterior.id, cidadeClicada.id));

            Line linhaRota = new Line(cidadeAnterior.x, cidadeAnterior.y, cidadeClicada.x, cidadeClicada.y);
            linhaRota.getStyleClass().add("rota-motorista-linha");
            mapaGrupo.getChildren().add(linhaRota);

            cidadeAnterior = cidadeClicada;
            circulo.getStyleClass().add("cidade-selecionada");
            lblStatus.setText("Trecho adicionado até: " + cidadeClicada.nome);

            // Adiciona as cidades seguintes na lista visual
            listViewPercurso.getItems().add(cidadeClicada.nome);
        }
    }

    @FXML
    void onMapaPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;

        anchorX = event.getSceneX() - mapaGrupo.getTranslateX();
        anchorY = event.getSceneY() - mapaGrupo.getTranslateY();

        containerMapa.setCursor(Cursor.CLOSED_HAND);
    }

    @FXML
    void onMapaClicked(MouseEvent mouseEvent) {
        containerMapa.setCursor(Cursor.DEFAULT);
    }

    @FXML
    void onMapaDragged(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() != MouseButton.PRIMARY) return;

        double novoX = mouseEvent.getSceneX() - anchorX;
        double novoY = mouseEvent.getSceneY() - anchorY;

        double larguraPainel = containerMapa.getWidth();
        double alturaPainel = containerMapa.getHeight();

        double larguraMapa = mapaGrupo.getBoundsInLocal().getWidth();
        double alturaMapa = mapaGrupo.getBoundsInLocal().getHeight();

        double maxX = 0;
        double maxY = 0;

        double minX = Math.min(0, larguraPainel - larguraMapa);
        double minY = Math.min(0, alturaPainel - alturaMapa);

        novoX = Math.clamp(novoX, minX, maxX);
        novoY = Math.clamp(novoY, minY, maxY);

        mapaGrupo.setTranslateX(novoX);
        mapaGrupo.setTranslateY(novoY);
    }
}
