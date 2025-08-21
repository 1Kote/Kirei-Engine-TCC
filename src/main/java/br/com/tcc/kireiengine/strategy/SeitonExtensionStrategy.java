package br.com.tcc.kireiengine.strategy;

import br.com.tcc.kireiengine.config.model.Configuration;
import br.com.tcc.kireiengine.config.model.SeitonRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Estratégia concreta que implementa a regra de organização do Seiton
 * baseada na extensão do arquivo. Esta classe é um "especialista" em
 * organizar arquivos por tipo.
 */
public class SeitonExtensionStrategy implements RuleStrategy
{
    // Define o logger para registrar todos os eventos desta estratégia.
    private static final Logger logger = LogManager.getLogger(SeitonExtensionStrategy.class);
    // Armazena a configuração carregada para ter acesso às regras de Seiton.
    private final Configuration config;

    /**
     * Construtor que recebe a configuração da aplicação.
     * @param config O objeto de configuração.
     */
    public SeitonExtensionStrategy(Configuration config)
    {
        // Atribui a configuração recebida à variável da classe.
        this.config = config;
    }

    /**
     * Aplica a lógica de organização por extensão ao arquivo fornecido.
     * @param filePath O caminho do arquivo a ser processado.
     */
    @Override
    public void apply(Path filePath)
    {
        // Tenta extrair a extensão do nome do arquivo; se não houver, retorna uma string vazia.
        String extension = getFileExtension(filePath.toString()).orElse("");
        // Condicional para verificar se a extensão foi encontrada.
        if(extension.isEmpty())
        {
            // Se não foi, encerra a execução para este arquivo, pois esta regra depende da extensão.
            return;
        }

        // Itera sobre cada regra de Seiton que foi definida no config.json.
        for(SeitonRule rule : config.getSeitonRules())
        {
            // Condicional para verificar se a lista de extensões da regra atual contém a extensão do nosso arquivo.
            if(rule.getExtensions().contains(extension))
            {
                // Se contém, loga qual regra foi correspondida e para qual arquivo.
                logger.info("[SEITON STRATEGY] Regra '{}' corresponde ao arquivo {}. Movendo...", rule.getName(), filePath);
                // Chama o método para mover o arquivo para o destino definido na regra.
                moveFile(filePath, rule.getDestination());
                // Encerra o método, pois a primeira regra correspondente já foi aplicada e o trabalho está feito.
                return;
            }
        }
    }

    /**
     * Move um arquivo da origem para o destino, com tratamento de erros específico
     * e organização em subpasta baseada na extensão.
     * @param source O caminho completo do arquivo de origem.
     * @param baseDestinationFolder O caminho da pasta de destino principal.
     */
    private void moveFile(Path source, String baseDestinationFolder)
    {
        // Tenta extrair a extensão do nome do arquivo para usar como nome da subpasta.
        getFileExtension(source.toString()).ifPresent(extension ->
        {
            try
            {
                // Constrói o caminho completo da subpasta de destino (ex: /.../Documentos/PDF).
                Path destinationPath = Paths.get(baseDestinationFolder, extension.toUpperCase());

                // Condicional para verificar se a subpasta de destino não existe.
                if (Files.notExists(destinationPath))
                {
                    // Log para informar que a subpasta será criada.
                    logger.info("Subpasta de destino não existe. Criando: {}", destinationPath);
                    // Cria a estrutura completa de diretórios para a subpasta.
                    Files.createDirectories(destinationPath);
                }

                // Define o caminho final do arquivo dentro da subpasta de destino.
                Path target = destinationPath.resolve(source.getFileName());

                // Condicional para verificar se um arquivo com o mesmo nome já existe no destino.
                if (Files.exists(target))
                {
                    // Se o arquivo já existe, gera um novo nome único (ex: relatorio_1.pdf).
                    target = generateUniqueFileName(target);
                    // Log para informar que um novo nome foi gerado.
                    logger.info("Arquivo já existe no destino. Usando nome único: {}", target.getFileName());
                }

                // Move o arquivo da origem para o destino final.
                Files.move(source, target);
                // Log de sucesso, informando qual arquivo foi movido e para onde.
                logger.info("SUCESSO: Arquivo '{}' movido para '{}'", source.getFileName(), target);
            }
            // Captura erros específicos de segurança (ex: falta de permissão para ler/escrever).
            catch (SecurityException e)
            {
                logger.error("FALHA DE PERMISSÃO: Não foi possível mover '{}'. Verifique as permissões do sistema.", source, e);
            }
            // Captura erros específicos de I/O (ex: disco cheio, arquivo em uso por outro programa).
            catch (IOException e)
            {
                logger.error("FALHA DE I/O: Não foi possível mover '{}'. O arquivo pode estar em uso ou o disco cheio.", source, e);
            }
            // Captura qualquer outro erro inesperado que não foi previsto.
            catch (Exception e)
            {
                logger.error("FALHA INESPERADA: Ocorreu um erro não previsto ao mover o arquivo '{}'.", source, e);
            }
        });
    }

    /**
     * Gera um nome de arquivo único se o destino já contiver um arquivo com o mesmo nome.
     * @param target O caminho de destino original.
     * @return Um novo caminho de destino com um nome de arquivo único.
     */
    private Path generateUniqueFileName(Path target)
    {
        // Obtém o nome completo do arquivo (ex: "relatorio.pdf").
        String fileName = target.getFileName().toString();
        // Variáveis para armazenar o nome base e a extensão.
        String baseName;
        String extension;

        // Encontra a posição do último ponto no nome do arquivo.
        int dotIndex = fileName.lastIndexOf('.');
        // Condicional para verificar se o arquivo tem uma extensão.
        if (dotIndex > 0)
        {
            // Se tiver, separa o nome base (ex: "relatorio").
            baseName = fileName.substring(0, dotIndex);
            // E a extensão (ex: ".pdf").
            extension = fileName.substring(dotIndex);
        } else
        {
            // Se não tiver, o nome base é o nome completo do arquivo.
            baseName = fileName;
            // E a extensão é uma string vazia.
            extension = "";
        }

        // Inicia um contador para adicionar ao nome do arquivo.
        int counter = 1;
        // Variável para armazenar o novo caminho de destino.
        Path uniqueTarget = target;
        // Loop que continua enquanto um arquivo com o nome de destino já existir.
        while (Files.exists(uniqueTarget))
        {
            // Constrói o novo nome (ex: "relatorio_1.pdf").
            String uniqueName = baseName + "_" + counter + extension;
            // Cria o novo caminho completo.
            uniqueTarget = target.getParent().resolve(uniqueName);
            // Incrementa o contador para a próxima tentativa.
            counter++;
        }
        // Retorna o novo caminho de destino único que foi encontrado.
        return uniqueTarget;
    }

    /**
     * Extrai a extensão de um nome de arquivo.
     * @param fileName O nome completo do arquivo.
     * @return Um Optional contendo a extensão em minúsculas, ou vazio se não houver.
     */
    private Optional<String> getFileExtension(String fileName)
    {
        // Retorna um Optional do nome do arquivo, que pode ser nulo.
        return Optional.ofNullable(fileName)
                // Garante que o nome do arquivo contém um ponto.
                .filter(f -> f.contains("."))
                // Se contiver, extrai a substring após o último ponto e a converte para minúsculas.
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1).toLowerCase());
    }
}