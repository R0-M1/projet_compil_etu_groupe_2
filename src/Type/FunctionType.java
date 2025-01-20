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
        if (!(t instanceof FunctionType)) {
            throw new IllegalArgumentException("Unification échouée : incompatible entre FunctionType et " + t);
        }

        FunctionType other = (FunctionType) t;
        Map<UnknownType, Type> substitutions = new java.util.HashMap<>();

        // Vérification du nombre d'arguments
        if (this.getNbArgs() != other.getNbArgs()) {
            throw new IllegalArgumentException("Unification échouée : le nombre d'arguments est différent.");
        }

        // Unification des paramètres
        for (int i = 0; i < this.getNbArgs(); i++) {
            Map<UnknownType, Type> argSubstitutions = this.getArgsType(i).unify(other.getArgsType(i));
            substitutions.putAll(argSubstitutions);
        }

        // Unification du retour
        Map<UnknownType, Type> returnSubstitutions = this.returnType.unify(other.returnType);
        substitutions.putAll(returnSubstitutions);

        // Application des substitutions pour mettre à jour immédiatement
        this.returnType = this.returnType.substituteAll(substitutions);
        for (int i = 0; i < argsTypes.size(); i++) {
            argsTypes.set(i, argsTypes.get(i).substituteAll(substitutions));
        }

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
