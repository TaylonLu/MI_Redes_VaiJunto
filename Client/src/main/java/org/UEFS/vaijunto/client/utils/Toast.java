package org.UEFS.vaijunto.client.utils;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Stack;

public final class Toast {

    private static final int TOAST_DURATION_MILLIS = 3500;
    private static final int FADE_DURATION_MILLIS = 500;
    private static final int MOVE_DURATION_MILLIS = 400;
    private static final double SPACING = 10.0;
    private static final String CSS_PATH = "/css/toast.css";

    private static AnchorPane toastPane;
    private static final Stack<AnchorPane> paneStack = new Stack<>();
    private static final ObservableList<Label> activeToasts = FXCollections.observableArrayList();

    /**
     * Instala o Toast em uma tela, dando permissão para que ele consiga
     * adicionar elementos na tela por um {@link AnchorPane} inserido
     * na raiz da cena.
     * @param stage Cena em que será instalado o Toast.
     */
    public static void install(Stage stage) {
        Objects.requireNonNull(stage, "Stage não pode ser nulo.");

        Parent root = stage.getScene().getRoot();
        if (!(root instanceof Pane rootPane)) {
            System.err.println("Toast Error: O nó raiz da cena precisa ser um Pane.");
            return;
        }

        AnchorPane newToastpane = new AnchorPane();
        newToastpane.setMouseTransparent(true);

        if (rootPane instanceof StackPane) {
            rootPane.getChildren().add(newToastpane);
        } else {
            newToastpane.prefHeightProperty().bind(rootPane.heightProperty());
            newToastpane.prefWidthProperty().bind(rootPane.widthProperty());
            rootPane.getChildren().add(newToastpane);
        }
        toastPane = newToastpane;
        try {
            toastPane.getStylesheets().add(Resources.getPath(CSS_PATH));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        paneStack.push(newToastpane);

        /* Adiciona um EventHandler para remover o pane (onde se exibe o Toast)
        no topo da pilha e o atualiza caso ainda exista um pane na pilha. */
        stage.setOnHidden(E -> {
            paneStack.pop();
            activeToasts.clear();
            toastPane = paneStack.isEmpty() ? null : paneStack.peek();
        });

        System.out.println("Toast instalado.");
    }

    /**
     * Exibe uma mensagem na tela com um estilo específico.
     * @param message Mensagem a ser exibida.
     */
    public static void success(String message) { show(message, "success"); }
    public static void error(String message) { show(message, "error"); }
    public static void warning(String message) { show(message, "warning"); }
    public static void info(String message) { show(message, "info"); }

    /**
     * Exibe a mensagem
     * @param message
     * @param styleClass
     */
    private static void show(final String message, final String styleClass) {
        Objects.requireNonNull(toastPane, "O Toast deve ser instalado com Toast.install(stage) primeiro.");

        Label label = new Label(message);
        label.setId("toast-label");
        label.getStyleClass().add(styleClass);
        label.setOpacity(0);

        AnchorPane.setRightAnchor(label, SPACING);
        AnchorPane.setBottomAnchor(label, 0.0);

        label.heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
                if (newValue.doubleValue() > 0) {
                    updateToastPositions();
                    label.heightProperty().removeListener(this);
                }
            }
        });

        toastPane.getChildren().add(label);
        activeToasts.add(label);

        Timeline lifecycle = new Timeline();
        lifecycle.setOnFinished(E -> {
            if (toastPane != null && toastPane.getChildren().contains(label)) {
                activeToasts.remove(label);
                toastPane.getChildren().remove(label);
                updateToastPositions();
            }
        });

        KeyFrame fadeInEnd = new KeyFrame(
                Duration.millis(FADE_DURATION_MILLIS),
                new KeyValue(label.opacityProperty(), 1.0));
        KeyFrame fadeOutStart = new KeyFrame(
                Duration.millis(TOAST_DURATION_MILLIS - FADE_DURATION_MILLIS),
                new KeyValue(label.opacityProperty(), 1.0));
        KeyFrame fadeOutEnd = new KeyFrame(
                Duration.millis(TOAST_DURATION_MILLIS),
                new KeyValue(label.opacityProperty(), 0.0));

        lifecycle.getKeyFrames().addAll(fadeInEnd, fadeOutStart, fadeOutEnd);
        lifecycle.play();
    }


    private static void updateToastPositions() {
        if (activeToasts.isEmpty()) return;

        double totalHeight = 0;
        for (int i = activeToasts.size()-1; i>= 0; i--) {
            Label label = activeToasts.get(i);

            double targetTranslateY = -totalHeight - SPACING;
            Timeline timeline = new Timeline();

            KeyValue KV = new KeyValue(label.translateYProperty(), targetTranslateY);
            KeyFrame KF = new KeyFrame(Duration.millis(MOVE_DURATION_MILLIS), KV);

            timeline.getKeyFrames().add(KF);
            timeline.play();
            totalHeight += label.getHeight() + SPACING;
        }
    }
}