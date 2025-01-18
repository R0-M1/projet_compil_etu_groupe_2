package Type;
import java.util.Map;

public class ArrayType extends Type{
    private Type tabType;

    /**
     * Constructeur
     * @param t type des éléments du tableau
     */
    public ArrayType(Type t) {
        this.tabType = t;
    }

    /**
     * Getter du type des éléments du tableau
     * @return type des éléments du tableau
     */
    public Type getTabType() {
        return tabType;
    }

    @Override
    public Map<UnknownType, Type> unify(Type t) {
        if (t instanceof ArrayType) {
            ArrayType other = (ArrayType) t;
            return this.tabType.unify(other.tabType);
        }
        throw new IllegalArgumentException("Unification échouée : incompatible entre array et " + t);
    }


    @Override
    public Type substitute(UnknownType v, Type t) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'substitute'");
    }

    @Override
    public boolean contains(UnknownType v) {
        return this.tabType.contains(v);
    }


    @Override
    public boolean equals(Object obj) {
        // Vérifie si les deux objets sont identiques en mémoire
        if (this == obj) return true;

        // Vérifie si l'objet à comparer est bien une instance d'ArrayType
        if (!(obj instanceof ArrayType)) return false;

        // Cast de l'objet pour accéder aux propriétés spécifiques d'ArrayType
        ArrayType other = (ArrayType) obj;

        // Vérifie que les types des éléments du tableau sont équivalents
        return this.tabType.equals(other.tabType);
    }


    @Override
    public String toString() {
        return "array[" + tabType.toString() + "]";
    }



}
