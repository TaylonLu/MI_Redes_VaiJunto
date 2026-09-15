package org.UEFS.vaijunto.client.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.UEFS.vaijunto.client.utils.Resources;
import org.UEFS.vaijunto.client.utils.Tela;
import org.UEFS.vaijunto.client.utils.Toast;

import java.util.Stack;

public class SceneManager {
    private static class CenaHistorico {
        Parent root;
        String titulo;
        Object controller;
        CenaHistorico(Parent root, String titulo, Object controller) {
            this.root = root;
            this.titulo = titulo;
            this.controller = controller;
        }
    }

    private static Object currentController;

    private static final Stack<CenaHistorico> historico = new Stack<>();
    private static Stage primaryStage;

    public static void inicializar(Stage stage) {
        primaryStage = stage;
    }

    public static void push(Tela tela) {
        push(tela.getCaminho(), tela.getNome());
    }

    public static void push(String fxmlPath, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(Resources.get(fxmlPath));
            Parent novaTela = loader.load();
            Object novoController = loader.getController();

            StackPane rootLayer = new StackPane();
            rootLayer.getChildren().add(novaTela); // Camada 0: A tela do FXML

            Scene cenaAtual = primaryStage.getScene();
            boolean naoEaPrimeiraTela = (cenaAtual != null && cenaAtual.getRoot() != null);

            if (titulo.equals(Tela.TELA_LOGIN.getNome()) || titulo.equals(Tela.TELA_CADASTRO.getNome())) {
                AnchorPane floatingLayer = new AnchorPane();
                floatingLayer.setPickOnBounds(false);

                Button btnVoltar = new Button("⬅ Voltar");

                btnVoltar.getStyleClass().add("btn-flutuante");
                btnVoltar.setOnAction(_ -> pop());

                AnchorPane.setTopAnchor(btnVoltar, 15.0);
                AnchorPane.setLeftAnchor(btnVoltar, 15.0);

                floatingLayer.getChildren().add(btnVoltar);
                rootLayer.getChildren().add(floatingLayer);

                historico.push(new CenaHistorico(cenaAtual.getRoot(), primaryStage.getTitle(), novoController));
                cenaAtual.setRoot(rootLayer);
            } else {
                primaryStage.setScene(new Scene(rootLayer, 1024, 672));
            }

            currentController = novoController;

            primaryStage.setTitle(titulo);
            Toast.install(primaryStage);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar FXML no push: " + fxmlPath, e);
        }
    }

    public static void pop() {
        if (!historico.isEmpty()) {
            CenaHistorico telaAnterior = historico.pop();
            primaryStage.getScene().setRoot(telaAnterior.root);
            primaryStage.setTitle(telaAnterior.titulo);
            currentController = telaAnterior.controller;
        } else {
            System.err.println("Não há telas para voltar.");
        }
    }

    public static Object getCurrentController() {
        return currentController;
    }
}