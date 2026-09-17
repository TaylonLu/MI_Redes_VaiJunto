import json
import uuid
import os

ARQUIVO_USUARIOS = 'data/usuarios.json'
QTD_CLIENTES = 50

def injetar_usuarios_teste():
    # 1. Carrega o arquivo existente ou cria um dicionário vazio
    usuarios = {}
    if os.path.exists(ARQUIVO_USUARIOS):
        with open(ARQUIVO_USUARIOS, 'r', encoding='utf-8') as f:
            try:
                usuarios = json.load(f)
            except json.JSONDecodeError:
                print("Arquivo usuarios.json vazio ou inválido. Criando um novo.")

    ids_gerados = []

    # 2. Gera os 50 usuários
    for i in range(QTD_CLIENTES):
        # Gera um UUID real para evitar problemas de validação no seu backend
        novo_id = f"user_{uuid.uuid4()}"
        ids_gerados.append(novo_id)

        usuarios[novo_id] = {
            "id": novo_id,
            "nome": f"Passageiro Stress Test {i+1}",
            "email": f"teste{i+1}@vaijunto.uefs.br",
            "senha": "senha_padrao_123",
            "motorista": False, # Ajuste a chave se no seu JSON for 'isMotorista'
            "caronas": []
        }

    # 3. Salva de volta no arquivo
    # Cria a pasta data se não existir
    os.makedirs(os.path.dirname(ARQUIVO_USUARIOS), exist_ok=True)

    with open(ARQUIVO_USUARIOS, 'w', encoding='utf-8') as f:
        json.dump(usuarios, f, indent=2, ensure_ascii=False)

    print(f"✅ {QTD_CLIENTES} usuários criados com sucesso no arquivo {ARQUIVO_USUARIOS}!")
    print("\n=======================================================")
    print("Copie o array abaixo e cole no seu ReservaStressTest.java:")
    print("=======================================================\n")

    # 4. Imprime o array Java pronto para uso
    java_array = '    private static final String[] TOKENS_TESTE = {\n        '
    java_array += ',\n        '.join([f'"{uid}"' for uid in ids_gerados])
    java_array += '\n    };'

    print(java_array)

if __name__ == "__main__":
    injetar_usuarios_teste()