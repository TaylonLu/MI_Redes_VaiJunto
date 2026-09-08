package org.UEFS.client.Controller;

import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.UEFS.client.utils.Resources;
import org.UEFS.vaijunto.Domain.FileManager;
import org.UEFS.vaijunto.Domain.Interfaces.Cidade;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MapaCaronaController {

    @FXML private Pane containerMapa;
    @FXML private Group mapaGrupo;
    @FXML private Label lblStatus;

    private final List<Cidade> cidades = new ArrayList<>();
    private final List<EstradaFX> estradas = new ArrayList<>();

    private Circle circuloOrigemEstrada = null;
    private Line linhaPreview = null;
    private double anchorX, anchorY;

    private static class EstradaFX {
        Cidade p1, p2;
        Line linhaVisivel;
        public EstradaFX(Cidade p1, Cidade p2, Line linhaVisivel) {
            this.p1 = p1; this.p2 = p2; this.linhaVisivel = linhaVisivel;
        }
    }

    @FXML
    public void initialize() {
        carregarCidades();
        carregarImagemFundo();
    }

    private void carregarImagemFundo() {
        try {
            InputStream resource = Resources.getStream("/images/mapa_kalos.jpg");
            ImageView imgFundo = new ImageView(new Image(resource));
            containerMapa.getChildren().add(imgFundo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void carregarCidades() {
        try {
            this.cidades.addAll(Objects.requireNonNull(FileManager.carregarDeJson("cidades.json")));
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void onMapaPressed(MouseEvent e) {
        if (e.isControlDown() && e.getButton() == MouseButton.PRIMARY) {
            anchorX = e.getSceneX() - mapaGrupo.getTranslateX();
            anchorY = e.getSceneY() - mapaGrupo.getTranslateY();
        }
    }

    @FXML
    void onMapaDragged(MouseEvent e) {
        if (e.isControlDown() && e.getButton() == MouseButton.PRIMARY) {
            mapaGrupo.setTranslateX(e.getSceneX() - anchorX);
            mapaGrupo.setTranslateY(e.getSceneY() - anchorY);
            lblStatus.setText("Movendo o mapa...");
        } else if (e.isAltDown() && circuloOrigemEstrada != null && linhaPreview != null) {
            double xLocal = e.getX() - mapaGrupo.getTranslateX();
            double yLocal = e.getY() - mapaGrupo.getTranslateY();
            linhaPreview.setEndX(xLocal);
            linhaPreview.setEndY(yLocal);
        }
    }

    @FXML
    void onMapaClicked(MouseEvent e) {
        double xLocal = e.getX() - mapaGrupo.getTranslateX();
        double yLocal = e.getY() - mapaGrupo.getTranslateY();

        if (e.isShiftDown() && e.getButton() == MouseButton.PRIMARY) {
            adicionarCidadeNoMapa("C" + (cidades.size() + 1), (int) xLocal, (int) yLocal);
        }
    }

    private void adicionarCidadeNoMapa(String nome, int x, int y) {
        Cidade cidade = new Cidade(nome, cidades.size() + 1, x, y, new ArrayList<>());
        cidades.add(cidade);

        Circle circulo = new Circle(x, y, 10);
        circulo.getStyleClass().add("cidade-no");
        circulo.setUserData(cidade);

        Text texto = new Text(x + 15, y + 5, nome);
        texto.setFont(Font.font("System", FontWeight.BOLD, 12));
        texto.getStyleClass().add("cidade-texto");

        circulo.setOnMouseClicked(e -> {
            if (e.isControlDown() && e.isAltDown()) {
                mapaGrupo.getChildren().removeAll(circulo, texto);
                cidades.remove(cidade);
                List<EstradaFX> paraRemover = estradas.stream().filter(est -> est.p1 == cidade || est.p2 == cidade).toList();
                for (EstradaFX est : paraRemover) {
                    mapaGrupo.getChildren().remove(est.linhaVisivel);
                    estradas.remove(est);
                }
                lblStatus.setText("Cidade removida.");
                e.consume();
            }
        });

        circulo.setOnMousePressed(e -> {
            if (e.isAltDown() && e.getButton() == MouseButton.PRIMARY) {
                circuloOrigemEstrada = circulo;
                linhaPreview = new Line(circulo.getCenterX(), circulo.getCenterY(), circulo.getCenterX(), circulo.getCenterY());
                linhaPreview.getStyleClass().add("estrada-preview");
                mapaGrupo.getChildren().add(linhaPreview);
                e.consume();
            }
        });

        circulo.setOnMouseReleased(e -> {
            if (e.isAltDown() && circuloOrigemEstrada != null && circuloOrigemEstrada != circulo) {
                Cidade c1 = (Cidade) circuloOrigemEstrada.getUserData();
                Cidade c2 = cidade;
                Line linhaEstrada = new Line(circuloOrigemEstrada.getCenterX(), circuloOrigemEstrada.getCenterY(), circulo.getCenterX(), circulo.getCenterY());
                linhaEstrada.getStyleClass().add("estrada-linha");
                
                linhaEstrada.setOnMouseClicked(ev -> {
                    if (ev.isControlDown() && ev.isAltDown()) {
                        mapaGrupo.getChildren().remove(linhaEstrada);
                        estradas.removeIf(est -> est.linhaVisivel == linhaEstrada);
                        lblStatus.setText("Estrada removida.");
                    }
                });
                
                mapaGrupo.getChildren().add(1, linhaEstrada);
                estradas.add(new EstradaFX(c1, c2, linhaEstrada));
                lblStatus.setText("Estrada conectada!");
            }
            if (linhaPreview != null) {
                mapaGrupo.getChildren().remove(linhaPreview);
                linhaPreview = null;
            }
            circuloOrigemEstrada = null;
        });

        mapaGrupo.getChildren().addAll(circulo, texto);
        lblStatus.setText(String.format("Cidade %s adicionada em X:%d Y:%d", nome, x, y));
    }

    @FXML
    void gerarCodigoParaConsole() {
        System.out.println("\n--- CÓDIGO GERADO PARA O SEU MAPA ---");
        for (Cidade C : cidades) {
            System.out.printf("Cidade c%d = new Cidade(\"%s\", %d, %d, %d, new ArrayList<>());\n", C.id, C.nome, C.id, C.x, C.y);
        }
        System.out.println("-------------------------------------\n");
        lblStatus.setText("Código gerado no console!");
    }
}