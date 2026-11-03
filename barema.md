# Critérios de Avaliação

1. **Arquitetura** — Explicar os componentes do sistema e seus papéis. Descrever como as responsabilidades foram divididas entre os servidores das empresas, quais dados cada servidor mantém e como os clientes motorista e passageiro se relacionam com eles. Comparar a nova arquitetura com a arquitetura centralizada do Problema 1, apontando o que deixou de ser possível e o que passou a ser necessário.

2. **Comunicação (API REST)** — Apresentar a especificação da API REST entre os servidores do consórcio: recursos, métodos, formato das requisições e respostas e códigos de retorno. Explicar como o sistema se comporta quando uma requisição não recebe resposta e o que acontece se a mesma requisição for repetida.

3. **Busca Distribuída** — Descrever como um servidor encontra itinerários que envolvem caronas publicadas em outras empresas. Explicar a estratégia adotada (consulta em tempo real, catálogo replicado ou outra) e como a busca se comporta quando o servidor de uma empresa está fora do ar.

4. **Exclusão Mútua** — Explicar como o sistema garante que o mesmo assento de um mesmo trecho nunca seja confirmado para dois passageiros, mesmo quando as requisições chegam simultaneamente por servidores de empresas diferentes. Justificar o mecanismo escolhido.

5. **Atomicidade Distribuída** — Explicar como a confirmação de um itinerário com trechos de empresas diferentes é realizada de forma atômica. Descrever o protocolo adotado, as mensagens trocadas, quem decide o resultado da transação e o que acontece com os trechos já reservados quando um dos participantes recusa. Apresentar o diagrama de sequência da confirmação.

6. **Precedência** — Explicar o critério adotado para decidir qual passageiro fica com o último assento de um trecho quando há requisições simultâneas vindas de servidores diferentes. Justificar o critério considerando que os relógios dos servidores não estão sincronizados e que as mensagens podem sofrer atrasos.

7. **Deadlock Distribuído** — Explicar como o sistema evita bloqueios mútuos quando dois passageiros tentam reservar, ao mesmo tempo, os mesmos trechos de empresas diferentes em ordens distintas. Descrever o mecanismo adotado e as suas consequências para o desempenho.

8. **Tolerância a Falhas** — Explicar o que acontece quando o servidor de uma empresa falha antes, durante e depois de uma confirmação de itinerário. Descrever como a falha é detectada pelos demais servidores, o que acontece com uma reserva em andamento e como as demais empresas continuam operando.

9. **Recuperação e Persistência** — Explicar como o servidor de uma empresa recupera o estado das reservas e das transações em andamento ao voltar a operar após uma falha. Descrever o que é armazenado, em que momento e como o sistema garante a consistência do consórcio após a recuperação.

10. **Testes** — Descrever os testes de software desenvolvidos para verificar a validade da solução, com foco em carga, concorrência entre servidores e injeção de falhas. Apresentar os resultados obtidos.

11. **Emulação** — Explicar como os servidores das empresas foram executados em contêineres Docker em computadores distintos do laboratório. Descrever como foi resolvida a comunicação entre eles e como a falha de uma empresa pode ser provocada e observada durante a apresentação.