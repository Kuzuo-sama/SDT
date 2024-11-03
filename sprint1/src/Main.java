import java.util.List;
import static java.lang.Thread.sleep;
public class Main {
    public static void main(String[] args) throws InterruptedException {
        Elemento e1 = new Elemento("A");
        Elemento e2 = new Elemento("B");
        Elemento e3 = new Elemento("C");

        Elemento.defineLider(e1);

        SendTransmitter st = new SendTransmitter();
        List<String> listMsgs = List.of("Mensagem 1", "Mensagem 2", "Mensagem 3");
        while (true){
            st.sendMessage(Elemento.getLider(), listMsgs);
            Thread.sleep(5000);
        }
    }
}