import java.util.List;

public class SendTransmitter extends Thread{
   public void sendMessage(Elemento lider, List<String> listMsgs){
       for (Elemento e : Elemento.getElementos()){
           if (e != lider){
               e.recebeMensagem(listMsgs.get(0));
           }
       }
   }
}
