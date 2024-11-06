public class Elemento {
    int lider;

    public Elemento(boolean isLeader) {
        this.lider = isLeader ? 1 : 0; // Se for líder, lider = 1; caso contrário, lider = 0
    }

    public boolean isLeader() {
        return lider == 1;
    }
}
