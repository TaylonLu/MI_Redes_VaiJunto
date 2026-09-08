package org.UEFS.client.utils;


import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;

/**
 * Classe para abstrair a lógica de pegar recursos (imagens, css, etc.)
 * Compatível com JAR.
 */
public final class Resources {
    private static final Class<?> RES = Resources.class;

    private Resources() {}

    /**
     * Verifica se um recurso existe.
     */
    public static boolean exists(String path) {
        if (path == null) return false;
        return RES.getResource(corrigirPath(path)) != null;
    }

    /**
     * Pega o caminho do arquivo em resources e o retorna como String.
     * @param path caminho relativo do arquivo com a pasta {@code resources}
     *            como raiz. Ex.: "/Images/Image.png"
     * @return String da URL do arquivo.
     * @throws InvalidParameterException Caso {@code path} seja null.
     * @throws FileNotFoundException Caso o arquivo não tenha sido encontrado.
     */
    public static String getPath(String path) throws InvalidParameterException, FileNotFoundException {
        URL result = get(path);
        return result.toExternalForm();
    }

    /**
     * Pega o InputStream de um recurso.
     * @param path Caminho do recurso.
     * @return {@link InputStream} do recurso.
     * @throws FileNotFoundException caso o recurso não exista ou o caminho esteja incorreto.
     */
    public static InputStream getStream(String path) throws FileNotFoundException {
        if (path == null) throw new InvalidParameterException("'path' não pode ser null.");

        InputStream IS = RES.getResourceAsStream(path);

        if (IS == null) throw new FileNotFoundException("Caminho '" + path + "' não encontrado");

        return IS;
    }

    /**
     * Pega o caminho do arquivo em resources e o retorna como {@link URL}.
     * @param path caminho relativo do arquivo com a pasta {@code resources}
     *            como raiz. Ex.: "/Images/Image.png"
     * @return  URL do arquivo.
     * @throws InvalidParameterException Caso {@code path} seja null.
     * @throws FileNotFoundException Caso o arquivo não tenha sido encontrado.
     */
    public static URL get(String path) throws FileNotFoundException {
        if (path == null) throw new InvalidParameterException("'path' não pode ser null.");

        URL result = RES.getResource(corrigirPath(path));

        if (result == null) throw new FileNotFoundException("Caminho '" + path + "' não encontrado");

        return result;
    }

    public static Path getAsPath(String path) throws FileNotFoundException, URISyntaxException {
        return Paths.get(get(path).toURI());
    }

    /**
     * Corrige o caminho para garantir que comece com "/".
     */
    private static String corrigirPath(String path) {
        if (path == null) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }
}