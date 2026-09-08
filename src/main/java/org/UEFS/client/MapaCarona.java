package org.UEFS.client;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.UEFS.client.utils.Resources;
import org.UEFS.vaijunto.Domain.FileManager;
import org.UEFS.vaijunto.Domain.Interfaces.Cidade;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MapaCarona extends Application {

    private final List<Cidade> cidades = new ArrayList<>();
    private final List<EstradaFX> estradas = new ArrayList<>();

    private Pane containerMapa;
    private Group mapaGrupo;
    private Label lblStatus;

    // Variáveis para auxílio de arrasto e criação de estradas
    private Circle circuloOrigemEstrada = null;
    private Line linhaPreview = null;
    private double anchorX, anchorY;

    private static class EstradaFX {
        Cidade p1, p2;
        Line linhaVisivel;

        public EstradaFX(Cidade p1, Cidade p2, Line linhaVisivel) {
            this.p1 = p1;
            this.p2 = p2;
            this.linhaVisivel = linhaVisivel;
        }
    }

    @Override
    public void start(Stage primaryStage) throws FileNotFoundException {
        BorderPane root = new BorderPane();
        carregarCidades();

        // 1. Painel de Controle (Topo)
        HBox painelControle = new HBox(10);
        painelControle.setPadding(new Insets(10));
        Button btnSalvar = new Button("Gerar Código no Console");
        btnSalvar.setOnAction(e -> gerarCodigoParaConsole());

        lblStatus = new Label("Ferramenta Pronta. Use os atalhos (Shift, Alt, Ctrl).");
        painelControle.getChildren().addAll(btnSalvar, lblStatus);
        root.setTop(painelControle);

        // 2. Container do Mapa
        mapaGrupo = new Group();
        containerMapa = new Pane(mapaGrupo);

        // Carrega Imagem de Fundo
        try {
            InputStream resource = Resources.getStream("/images/mapa_kalos.jpg");
            ImageView imgFundo = new ImageView(new Image(resource));
            mapaGrupo.getChildren().add(imgFundo);
        } catch (Exception e) {
            e.printStackTrace();
        }

        root.setCenter(containerMapa);

        // Configuração dos Eventos do Mouse no Mapa
        configurarEventosMapa();

        Scene scene = new Scene(root, 1024, 672);

        // Aplicação do estilo CSS
        scene.getStylesheets().add(Resources.getPath("/css/style.css"));

        primaryStage.setTitle("Vai Junto - Construtor de Mapa (JavaFX)");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void configurarEventosMapa() {
        // Controle de Pan (Mover Câmera com Ctrl + Drag em área vazia)
        containerMapa.setOnMousePressed(e -> {
            if (e.isControlDown() && e.getButton() == MouseButton.PRIMARY) {
                anchorX = e.getSceneX() - mapaGrupo.getTranslateX();
                anchorY = e.getSceneY() - mapaGrupo.getTranslateY();
            }
        });

        containerMapa.setOnMouseDragged(e -> {
            if (e.isControlDown() && e.getButton() == MouseButton.PRIMARY) {
                mapaGrupo.setTranslateX(e.getSceneX() - anchorX);
                mapaGrupo.setTranslateY(e.getSceneY() - anchorY);
                lblStatus.setText("Movendo o mapa...");
            } else if (e.isAltDown() && circuloOrigemEstrada != null && linhaPreview != null) {
                // Atualiza o preview da linha durante o arrasto
                double xLocal = e.getX() - mapaGrupo.getTranslateX();
                double yLocal = e.getY() - mapaGrupo.getTranslateY();
                linhaPreview.setEndX(xLocal);
                linhaPreview.setEndY(yLocal);
            }
        });

        containerMapa.setOnMouseClicked(e -> {
            double xLocal = e.getX() - mapaGrupo.getTranslateX();
            double yLocal = e.getY() - mapaGrupo.getTranslateY();

            // Adicionar Cidade (Shift + Click)
            if (e.isShiftDown() && e.getButton() == MouseButton.PRIMARY) {
                adicionarCidadeNoMapa("C" + (cidades.size() + 1), (int) xLocal, (int) yLocal);
            }
        });
    }

    private void adicionarCidadeNoMapa(String nome, int x, int y) {
        Cidade cidade = new Cidade(nome, cidades.size() + 1, x, y, new ArrayList<>());
        cidades.add(cidade);

        // Círculo Representando a Cidade
        Circle circulo = new Circle(x, y, 10);
        circulo.getStyleClass().add("cidade-no"); // Classe CSS para estilo base e hover
        circulo.setUserData(cidade);

        // Texto com o Nome da Cidade
        Text texto = new Text(x + 15, y + 5, nome);
        texto.setFont(Font.font("System", FontWeight.BOLD, 12));
        texto.getStyleClass().add("cidade-texto");

        // Eventos do Elemento da Cidade (Mouse)
        circulo.setOnMouseClicked(e -> {
            // Remover Cidade (Ctrl + Alt + Click)
            if (e.isControlDown() && e.isAltDown()) {
                mapaGrupo.getChildren().removeAll(circulo, texto);
                cidades.remove(cidade);

                // Remove estradas conectadas
                List<EstradaFX> paraRemover = estradas.stream()
                        .filter(est -> est.p1 == cidade || est.p2 == cidade)
                        .toList();

                for (EstradaFX est : paraRemover) {
                    mapaGrupo.getChildren().remove(est.linhaVisivel);
                    estradas.remove(est);
                }
                lblStatus.setText("Cidade removida.");
                e.consume();
            }
        });

        // Início do desenho de estrada (Alt + Drag)
        circulo.setOnMousePressed(e -> {
            if (e.isAltDown() && e.getButton() == MouseButton.PRIMARY) {
                circuloOrigemEstrada = circulo;
                linhaPreview = new Line(circulo.getCenterX(), circulo.getCenterY(), circulo.getCenterX(), circulo.getCenterY());
                linhaPreview.getStyleClass().add("estrada-preview");
                mapaGrupo.getChildren().add(linhaPreview);
                e.consume();
            }
        });

        // Final do desenho de estrada (Alt + Release no nó de destino)
        circulo.setOnMouseReleased(e -> {
            if (e.isAltDown() && circuloOrigemEstrada != null && circuloOrigemEstrada != circulo) {
                Cidade c1 = (Cidade) circuloOrigemEstrada.getUserData();
                Cidade c2 = cidade;

                Line linhaEstrada = new Line(circuloOrigemEstrada.getCenterX(), circuloOrigemEstrada.getCenterY(), circulo.getCenterX(), circulo.getCenterY());
                linhaEstrada.getStyleClass().add("estrada-linha");

                // Permite remover a estrada ao clicar com Ctrl+Alt
                linhaEstrada.setOnMouseClicked(ev -> {
                    if (ev.isControlDown() && ev.isAltDown()) {
                        mapaGrupo.getChildren().remove(linhaEstrada);
                        estradas.removeIf(est -> est.linhaVisivel == linhaEstrada);
                        lblStatus.setText("Estrada removida.");
                    }
                });

                // Insere a linha atrás dos nós no grafo de cena
                mapaGrupo.getChildren().add(1, linhaEstrada);
                estradas.add(new EstradaFX(c1, c2, linhaEstrada));
                lblStatus.setText("Estrada conectada!");
            }

            // Limpa o preview
            if (linhaPreview != null) {
                mapaGrupo.getChildren().remove(linhaPreview);
                linhaPreview = null;
            }
            circuloOrigemEstrada = null;
        });

        mapaGrupo.getChildren().addAll(circulo, texto);
        lblStatus.setText(String.format("Cidade %s adicionada em X:%d Y:%d", nome, x, y));
    }

    private void gerarCodigoParaConsole() {
        System.out.println("\n--- CÓDIGO GERADO PARA O SEU MAPA ---");
        for (Cidade C : cidades) {
            System.out.printf("Cidade c%d = new Cidade(\"%s\", %d, %d, %d, new ArrayList<>());\n", C.id, C.nome, C.id, C.x, C.y);
        }
        System.out.println("-------------------------------------\n");
        lblStatus.setText("Código gerado no console!");
    }

    public void carregarCidades() {
        try {
            this.cidades.addAll(Objects.requireNonNull(FileManager.carregarDeJson("cidades.json")));
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}