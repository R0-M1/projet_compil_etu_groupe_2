package Type;
import java.util.Map;
import java.util.HashMap;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class UnknownType extends Type {
    private String varName;
    private int varIndex;
    private static int newVariableCounter = 0;

    /**
     * Constructeur sans nom
     */
    public UnknownType(){
        this.varIndex = newVariableCounter++;
        this.varName = "#";
    }

    /**
     * Constructeur à partir d'un nom de variable et un numéro
     * @param s nom de variable
     * @param n numéro de la variable
     */
    public UnknownType(String s, int n)  {
        this.varName = s;
        this.varIndex = n;
    }

    /**
     * Constructeur à partir d'un ParseTree (standardisation du nom de variable)
     * @param ctx ParseTree
     */
    public UnknownType(ParseTree ctx) {
        this.varName = ctx.getText();
        if (ctx instanceof TerminalNode) {
            this.varIndex = ((TerminalNode)ctx).getSymbol().getStartIndex();
        } else {
            if (ctx instanceof ParserRuleContext) {
                this.varIndex = ((ParserRuleContext)ctx).getStart().getStartIndex();
            }
            else {
                throw new Error("Illegal UnknownType construction");
            }
        }
    }

    /**
     * Getter du nom de variable de type
     * @return variable de type
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Getter du numéro de variable de type
     * @return numéro de variable de type
     */
    public int getVarIndex() {
        return varIndex;
    }

    /**
     * Setter du numéro de variable de type
     * @param n numéro de variable de type
     */
    public void setVarIndex(int n) {
        this.varIndex = n;
    }

    @Override
    public Map<UnknownType, Type> unify(Type t) {
        // Résultat des unifications
        Map<UnknownType, Type> unification = new HashMap<>();

        // Cas 1 : Unification avec soi-même
        if (this.equals(t)) {
            return unification;
        }

        // Cas 2 : Unification avec un autre UnknownType
        if (t instanceof UnknownType) {
            UnknownType other = (UnknownType) t;
            unification.put(this, other);
            return unification;
        }

        // Cas 3 : Unification avec un type concret
        if (!(t instanceof UnknownType)) {
            if (t.contains(this)) {
                throw new IllegalArgumentException("Unification échoué : dépendance circulaire détectée");
            }
            unification.put(this, t); //return une hashmap avec type et auto
            return unification;
        }

        // Si aucun des cas n'est satisfait, l'unification échoue
        return null;
    }


    @Override
    public Type substitute(UnknownType v, Type t) {
        // Si l'instance actuelle est égale à l'UnknownType cible, on remplace par le type donné
        if (this.equals(v)) {
            return t; // Effectue la substitution
        }
        // Si ce n'est pas égal, retourne l'instance actuelle (aucune substitution nécessaire ici)
        return this;
    }


    @Override
    public boolean contains(UnknownType v) {
        // Vérifie si l'instance actuelle est égale à l'UnknownType donné
        return this.equals(v);
    }

    @Override
    public boolean equals(Object obj) {
        // Vérifie si c'est la même instance
        if (this == obj) {
            return true;
        }

        // Vérifie si l'objet est une instance de UnknownType
        if (obj instanceof UnknownType) {
            UnknownType other = (UnknownType) obj;

            // Compare les noms et les indices des variables
            return this.varName.equals(other.varName) && this.varIndex == other.varIndex;
        }

        // Si ce n'est pas un UnknownType, retourne false
        return false;
    }



    @Override
    public String toString() {
        return "UnknownType(varName=" + varName + ", varIndex=" + varIndex + ")";
    }


}
