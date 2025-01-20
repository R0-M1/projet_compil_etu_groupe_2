package Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FunctionType extends Type {
    private Type returnType;
    private ArrayList<Type> argsTypes;

    /**
     * Constructeur
     * @param returnType type de retour
     * @param argsTypes liste des types des arguments
     */
    public FunctionType(Type returnType, ArrayList<Type> argsTypes) {
        this.returnType = returnType;
        this.argsTypes = argsTypes;
    }

    /**
     * Getter du type de retour
     * @return type de retour
     */
    public Type getReturnType() {
        return returnType;
    }

    /**
     * Getter du type du i-eme argument
     * @param i entier
     * @return type du i-eme argument
     */
    public Type getArgsType(int i) {
        return argsTypes.get(i);
    }

    /**
     * Getter du nombre d'arguments
     * @return nombre d'arguments
     */

    /**
     * Getter for the list of argument types
     * @return list of argument types
     */
    public List<Type> getArgsTypes() {
        return new ArrayList<>(argsTypes); // Return a copy to ensure encapsulation
    }

    public int getNbArgs() {
        return argsTypes.size();
    }

    @Override
    public Map<UnknownType, Type> unify(Type t) {
        // Vérifie si `t` est un FunctionType
        if (!(t instanceof FunctionType)) {
            throw new IllegalArgumentException("Unification échouée : incompatible entre FunctionType et " + t);
        }

        FunctionType other = (FunctionType) t;
        Map<UnknownType, Type> substitutions = new java.util.HashMap<>();

        // Vérifie que le nombre d'arguments est identique
        if (this.getNbArgs() != other.getNbArgs()) {
            throw new IllegalArgumentException("Unification échouée : le nombre d'arguments est différent.");
        }

        // Unifie les types des arguments
        for (int i = 0; i < this.getNbArgs(); i++) {
            Map<UnknownType, Type> argSubstitutions = this.getArgsType(i).unify(other.getArgsType(i));
            if (argSubstitutions == null) {
                throw new IllegalArgumentException("Unification échouée : les arguments " + i + " ne peuvent pas être unifiés.");
            }
            substitutions.putAll(argSubstitutions);
        }

        // Unifie les types de retour
        Map<UnknownType, Type> returnSubstitutions = this.returnType.unify(other.returnType);
        if (returnSubstitutions == null) {
            throw new IllegalArgumentException("Unification échouée : les types de retour sont incompatibles.");
        }
        substitutions.putAll(returnSubstitutions);

        return substitutions;
    }


    @Override
    public Type substitute(UnknownType v, Type t) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'substitute'");
    }

    @Override
    public boolean contains(UnknownType v) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'contains'");
    }

    @Override
    public boolean equals(Object t) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'equals'");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FunctionType(");
        sb.append("returnType=").append(returnType);
        sb.append(", argsTypes=[");
        for (int i = 0; i < argsTypes.size(); i++) {
            sb.append(argsTypes.get(i));
            if (i < argsTypes.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("])");
        return sb.toString();
    }


}
