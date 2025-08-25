package br.com.tcc.kireiengine.strategy;

import br.com.tcc.kireiengine.config.model.CleanTemporaryFoldersRule;
import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeisoConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Strategy para limpeza de pastas temporárias (Seiso)
 */
public class SeisoTempFoldersStrategy implements ScheduledTaskStrategy
{
    private static final Logger logger = LogManager.getLogger(SeisoTempFoldersStrategy.class);

    @Override
    public void execute(Configuration config)
    {
        SeisoConfig seisoConfig = config.getSeisoConfig();

        //Validação se Seiso está habilitado e configurado
        if (seisoConfig == null || !seisoConfig.isEnabled() ||
                seisoConfig.getRules() == null ||
                seisoConfig.getRules().getCleanTemporaryFolders() == null)
        {
            logger.debug("SEISO STRATEGY: Limpeza de pastas temporárias não configurada.");
            return;
        }

        CleanTemporaryFoldersRule rule = seisoConfig.getRules().getCleanTemporaryFolders();

        //Validação se regra específica está habilitada
        if (!rule.isEnabled())
        {
            logger.info("SEISO STRATEGY: Limpeza de pastas temporárias desabilitada.");
            return;
        }

        logger.info("[SEISO STRATEGY] Iniciando limpeza de pastas temporárias...");

        //Iteração sobre cada pasta configurada
        for (String folderPath : rule.getFolders())
        {
            Path path = Paths.get(folderPath);

            //Validação se pasta existe
            if (Files.notExists(path))
            {
                logger.warn("SEISO STRATEGY: Pasta não existe: {}", path);
                continue;
            }

            try
            {
                //Walk recursivo e limpeza ordenada (arquivos antes de pastas)
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(path)) // Não deleta a pasta raiz
                        .forEach(this::deleteFileOrFolder);

                logger.info("SEISO STRATEGY: Limpeza concluída: {}", path);
            }
            catch (IOException e)
            {
                logger.error("SEISO STRATEGY: Erro ao limpar pasta {}", path, e);
            }
        }
    }

    /**
     * Deleta arquivo ou pasta individual
     */
    private void deleteFileOrFolder(Path path)
    {
        try
        {
            Files.delete(path);
            logger.debug("SEISO STRATEGY: Deletado: {}", path);
        }
        catch (IOException e)
        {
            logger.warn("SEISO STRATEGY: Não foi possível deletar {}: {}", path, e.getMessage());
        }
    }
}