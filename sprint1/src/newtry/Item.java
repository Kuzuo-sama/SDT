package newtry;


public class Item {
    private String nome;
    private String conteudo;

    public Item(String nome, String conteudo) {
        this.nome = nome;
        this.conteudo = conteudo;
    }

    public String getNome() {
        return nome;
    }

    public String getConteudo() {
        return conteudo;
    }

    public String toString(String nome, String conteudo) {
        return "Item{nome='" + nome + "', conteudo='" + conteudo + "'}";
    }
}
