package Type;
import java.util.Map;

public  class PrimitiveType extends Type {
    private Type.Base type;

    /**
     * Constructeur
     * @param type type de base
     */
    public PrimitiveType(Type.Base type) {
        this.type = type;
    }

    /**
     * Getter du type
     * @return type
     */
    public Type.Base getType() {
        return type;
    }

    @Override
    public Map<UnknownType, Type> unify(Type t) {
        // Si `t` est un PrimitiveType et son type est égal à celui-ci
        if (t instanceof PrimitiveType && this.equals(t)) {
            return Map.of();
        }

        // Si `t` est un UnknownType, déléguer l'unification à l'UnknownType
        if (t instanceof UnknownType) {
            return t.unify(this); // Appelle unify sur l'UnknownType
        }

        // Si le type est incompatible, l'unification échoue
        throw new IllegalArgumentException("Unification échouée : incompatible entre " + this + " et " + t);
    }

    @Override
    public Type substitute(UnknownType v, Type t) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'substitute'");
    }

    @Override
    public boolean contains(UnknownType v) {
        // Les types primitifs ne contiennent jamais d'UnknownType
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        // Vérifie si c'est la même instance
        if (this == obj) {
            return true;
        }

        // Vérifie si l'objet est une instance de PrimitiveType
        if (obj instanceof PrimitiveType) {
            PrimitiveType other = (PrimitiveType) obj;

            // Compare les types de base
            return this.type == other.type;
        }

        // Si ce n'est pas un PrimitiveType, retourne false
        return false;
    }


    @Override
    public String toString() {
        return type.name().toLowerCase();
    }


}
