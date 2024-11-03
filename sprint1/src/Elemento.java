import java.util.ArrayList;
import java.util.List;

public class Elemento {
    private static Elemento nome;
    String elemento;
    private static List<Elemento> elementos = new ArrayList<>();

    public Elemento(String elemento){
        this.elemento = elemento;
        elementos.add(this);
    }

    public static void defineLider(Elemento lider){
        nome = lider;
    }

    public static Elemento getLider(){
        return nome;
    }

    public String getElemento(){
        return elemento;
    }

    public static List<Elemento> getElementos(){
        return elementos;
    }

    public void recebeMensagem(String mensagem){
        System.out.println("Mensagem recebida: " + mensagem);
    }

}
