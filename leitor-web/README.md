# Leitor Dinâmico NIPI — versão web

Mesma mecânica do app Android, rodando no navegador. Serve para testar o ritmo
sem precisar compilar nada, e para ler no celular sem instalar aplicativo.

Site estático puro: um HTML, um manifesto e dois ícones. Sem dependência externa,
sem build, sem servidor.

## Endereço

Publicado pelo GitHub Pages deste repositório:

```
https://nipi-br.github.io/Estudo/leitor-web/
```

Se o endereço não abrir, o Pages ainda não está ligado. Em **Settings → Pages**,
escolha a branch `main` e a pasta `/ (root)`. A publicação leva um ou dois minutos.

## Como se usa

Deite o telefone.

| Gesto | O que faz |
|---|---|
| Segurar o **lado direito** | Avança as palavras na velocidade configurada |
| Segurar o **lado esquerdo** | Volta as palavras na mesma velocidade |
| **Dois toques** no lado esquerdo | Volta de uma vez a quantidade de palavras configurada |
| Toque simples | Anda uma palavra só |

No Android, o menu do Chrome tem **Adicionar à tela de início**: o leitor passa a
abrir em tela cheia, deitado, com ícone próprio — igual a um aplicativo.

## Formatos

- **EPUB** — o ZIP é lido na mão e descomprimido pelo próprio navegador, com os
  capítulos na ordem da espinha do livro. Sem biblioteca externa.
- **TXT, HTML e Markdown** — direto.
- **PDF não abre aqui.** Extrair texto de PDF exige uma biblioteca que só existe
  no app Android. É a única diferença de funcionalidade entre as duas versões.

## Onde ficam os livros

No próprio navegador: o texto vai para o IndexedDB e as preferências para o
localStorage. Nada sai do aparelho, e nada é enviado para servidor nenhum.

Como consequência, os livros não passam de um aparelho para outro, e limpar os
dados do navegador apaga a biblioteca.

## O que falta

- Funcionar sem internet depois de aberto (exigiria um service worker).
- PDF.
- Ajustar a velocidade sem sair da leitura.
