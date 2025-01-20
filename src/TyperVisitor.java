import java.util.*;
// Import des types utilisés
import Type.ArrayType;

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

import Type.Type;
import Type.UnknownType;
import Type.PrimitiveType;
import Type.FunctionType;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Cette classe est un visiteur qui sert à analyser et déterminer les types
 * des expressions et variables dans un programme TCL.
 * <p>
 * Elle hérite de {@link AbstractParseTreeVisitor} et implémente {@link grammarTCLVisitor},
 * pour parcourir l'arbre généré par ANTLR et vérifier (ou inférer) les types.
 */
public class TyperVisitor extends AbstractParseTreeVisitor<Type> implements grammarTCLVisitor<Type> {
    /**
     * Map global reliant des UnknownType à des Type concrets.
     */
    private Map<UnknownType,Type> types = new HashMap<UnknownType,Type>();

    /**
     * Pile représentant les différents “scopes” (blocs) avec leurs variables.
     * Chaque élément de la pile est un Map associant un UnknownType à un Type.
     */
    private Stack<Map<UnknownType, Type>> typeScopes = new Stack<>();

    /**
     * Pile archivant les anciens scopes une fois qu'on en est sortis.
     * Utile pour garder un historique des déclarations.
     */
    private Stack<Map<UnknownType, Type>> archivedScopes = new Stack<>();

    /**
     * Map interne qui relie deux UnknownType pour exprimer qu'ils sont équivalents
     * (ou doivent le devenir lors de la résolution des types).
     */
    private Map<UnknownType, UnknownType> autoLinkMap = new HashMap<>();

    /**
     * Constructeur par défaut.
     * Il crée un premier scope global, ainsi qu'un scope archivé global.
     */
    public TyperVisitor() {
        typeScopes.push(new HashMap<>()); // Scope global
        archivedScopes.push(new HashMap<>()); // Scope global
    }

    /**
     * Trouve la racine représentative d'un UnknownType dans le autoLinkMap.
     * Cela permet de gérer l'équivalence entre plusieurs UnknownType.
     *
     * @param type l'UnknownType à explorer
     * @return la racine de l'UnknownType (lui-même ou une autre instance)
     */
    private UnknownType find(UnknownType type) {
        if (!autoLinkMap.containsKey(type)) {
            return type; // Si aucun lien, retourne lui-même (racine de son propre groupe)
        }
        // Compression de chemin pour optimisation
        UnknownType root = find(autoLinkMap.get(type)); // Trouve la racine récursivement
        autoLinkMap.put(type, root); // Compression de chemin : met à jour pour pointer directement sur la racine
        return root;
    }


    /**
     * Relie deux UnknownType entre eux dans le autoLinkMap en déclarant qu'ils sont équivalents.
     *
     * @param type1 premier UnknownType
     * @param type2 second UnknownType
     */
    private void union(UnknownType type1, UnknownType type2) {
        UnknownType root1 = find(type1);
        UnknownType root2 = find(type2);
        if (!root1.equals(root2)) {
            autoLinkMap.put(root1, root2); // Relie le premier représentant au second

        }
    }

    /**
     * Établit un lien entre deux UnknownType si les deux sont effectivement des UnknownType.
     * Utile pour gérer les cas où on assigne un UnknownType à un autre.
     *
     * @param declaredKey         clé (UnknownType) du côté gauche de l'opération
     * @param declaredType        type concret (ou UnknownType) du côté gauche
     * @param rightVariableName   nom de la variable ou expression à droite
     * @param rightType           type concret (ou UnknownType) du côté droit
     */
    private void linkUnknownTypes(UnknownType declaredKey, Type declaredType, String rightVariableName, Type rightType) {
        // Vérifie que les deux types sont des UnknownType
        if (declaredType instanceof UnknownType && rightType instanceof UnknownType) {


            // Recherche de la clé de la variable à droite dans les scopes
            Map.Entry<UnknownType, Type> foundRightEntry = existsInAllScopes(rightVariableName);
            if (foundRightEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + rightVariableName);
            }

            UnknownType rightKey = foundRightEntry.getKey();


            // Établit la relation via union
            union(declaredKey, rightKey);
        }
    }

    /**
     * Propage un type concret à tous les UnknownType qui sont liés
     * (donc qui partagent la même racine dans autoLinkMap).
     *
     * @param type     l'UnknownType qu'on veut substituer
     * @param realType le type réel qui va remplacer
     */
    private void propagateType(UnknownType type, Type realType) {
        // Trouve la racine canonique
        UnknownType root = find(type);


        // Mise à jour dans tous les scopes ET dans la table de types globale
        for (Map.Entry<UnknownType, Type> entry : types.entrySet()) {
            if (find(entry.getKey()).equals(root)) {
                applySubstitutionToScope(entry.getKey(), realType);
                types.put(entry.getKey(), realType);
            }
        }

        // S'assurer que tous les types liés sont mis à jour
        for (Map.Entry<UnknownType, UnknownType> entry : autoLinkMap.entrySet()) {
            if (find(entry.getKey()).equals(root)) {
                applySubstitutionToScope(entry.getKey(), realType);
                types.put(entry.getKey(), realType);
            }
        }

        // Mise à jour de la racine
        applySubstitutionToScope(root, realType);
        types.put(root, realType);


    }

    /**
     * Force la variable donnée à devenir un type INT, si c'est un UnknownType.
     * Propage ensuite cette information dans tous les scopes.
     *
     * @param variableName nom de la variable à unifier
     * @param variableType type actuel de la variable
     */
    private void unifyToInt(String variableName, Type variableType) {
        if (variableType instanceof UnknownType) {

            Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);
            if (foundEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + variableName);
            }

            UnknownType key = foundEntry.getKey();

            propagateType(key, new PrimitiveType(Type.Base.INT));
        }
    }

    /**
     * Force la variable donnée à devenir un type BOOL, si c'est un UnknownType.
     * Propage ensuite cette information dans tous les scopes.
     *
     * @param variableName nom de la variable à unifier
     * @param variableType type actuel de la variable
     */
    private void unifyToBool(String variableName, Type variableType) {
        // Vérifie si le type est un UnknownType
        if (variableType instanceof UnknownType) {


            // Recherche la clé de la variable dans tous les scopes
            Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);
            if (foundEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + variableName);
            }

            UnknownType key = foundEntry.getKey();


            // Effectue l'unification avec BOOL
            Map<UnknownType, Type> unification = key.unify(new PrimitiveType(Type.Base.BOOL));


            // Propage les changements
            for (Map.Entry<UnknownType, Type> entry : unification.entrySet()) {
                propagateType(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * @return la pile des scopes (du plus local au global)
     */
    public Stack<Map<UnknownType, Type>> getTypeScopes() {
        return typeScopes;
    }

    /**
     * @return la pile des scopes archivés (scopes fermés)
     */
    public Stack<Map<UnknownType, Type>> getarchivedScopes() {
        return archivedScopes;
    }

    /**
     * @return la Map globale des UnknownType et leur Type associé
     */
    public Map<UnknownType, Type> getTypes() {
        return types;
    }

    /**
     * Vérifie si une variable avec un certain nom existe déjà dans le scope donné.
     *
     * @param variableName nom de la variable à chercher
     * @param currentScope scope dans lequel on cherche
     * @return true si la variable est trouvée, sinon false
     */
    private boolean VarExistsInCurrentScope(String variableName, Map<UnknownType, Type> currentScope) {
        for (UnknownType key : currentScope.keySet()) {
            if (key.getVarName().equals(variableName)) {
                return true; // La variable existe déjà dans le scope courant
            }
        }
        return false; // La variable n'existe pas dans le scope courant
    }

    /**
     * Vérifie si une fonction avec un certain nom existe déjà dans le scope donné.
     *
     * @param functionName nom de la fonction à chercher
     * @param currentScope scope dans lequel on cherche
     * @return true si la fonction est trouvée, sinon false
     */
    private boolean FunctionExistsInCurrentScope(String functionName, Map<UnknownType, Type> currentScope) {
        for (Map.Entry<UnknownType, Type> entry : currentScope.entrySet()) {
            UnknownType key = entry.getKey();
            Type value = entry.getValue();

            // Vérifie si le nom correspond et si la valeur est une instance de FunctionType
            if (key.getVarName().equals(functionName) && value instanceof FunctionType) {
                return true; // La fonction existe déjà dans le scope courant
            }
        }
        return false; // La fonction n'existe pas dans le scope courant
    }

    /**
     * Vérifie si une variable existe déjà dans un scope parent (pas le scope courant, mais ceux au-dessus).
     *
     * @param variableName nom de la variable à chercher
     * @return true si la variable est trouvée, sinon false
     */
    private boolean VarExistsInParentScopes(String variableName) {
        for (int i = typeScopes.size() - 2; i >= 0; i--) { // Parcourt les scopes parents
            Map<UnknownType, Type> parentScope = typeScopes.get(i);
            for (UnknownType key : parentScope.keySet()) {
                if (key.getVarName().equals(variableName)) {
                    return true; // La variable existe dans un scope parent
                }
            }
        }
        return false; // La variable n'existe pas dans les scopes parents
    }

    /**
     * Vérifie si une fonction existe déjà dans un scope parent (pas le scope courant, mais ceux au-dessus).
     *
     * @param functionName nom de la fonction à chercher
     * @return true si la fonction est trouvée, sinon false
     */
    private boolean FunctionExistsInParentScopes(String functionName) {
        // Iterate through parent scopes from the second-to-last to the root
        for (int i = typeScopes.size() - 2; i >= 0; i--) {
            Map<UnknownType, Type> parentScope = typeScopes.get(i);
            for (Map.Entry<UnknownType, Type> entry : parentScope.entrySet()) {
                // Check if the variable name matches and if it is a FunctionType
                if (entry.getKey().getVarName().equals(functionName) && entry.getValue() instanceof FunctionType) {
                    return true; // The function exists in a parent scope
                }
            }
        }
        return false; // The function does not exist in the parent scopes
    }

    /**
     * Recherche dans tous les scopes (du plus local au global) le FunctionType associé
     * à la fonction demandée.
     *
     * @param functionName nom de la fonction
     * @return le FunctionType si trouvé, sinon null
     */
    private FunctionType findFunctionType(String functionName) {
        // Check the current scope
        Map<UnknownType, Type> currentScope = typeScopes.peek();
        for (Map.Entry<UnknownType, Type> entry : currentScope.entrySet()) {
            if (entry.getKey().getVarName().equals(functionName) && entry.getValue() instanceof FunctionType) {
                return (FunctionType) entry.getValue();
            }
        }

        // Check the parent scopes
        for (int i = typeScopes.size() - 2; i >= 0; i--) {
            Map<UnknownType, Type> parentScope = typeScopes.get(i);
            for (Map.Entry<UnknownType, Type> entry : parentScope.entrySet()) {
                if (entry.getKey().getVarName().equals(functionName) && entry.getValue() instanceof FunctionType) {
                    return (FunctionType) entry.getValue();
                }
            }
        }

        // If not found in any scope
        return null;
    }

    /**
     * Applique une substitution (mise à jour d'un UnknownType) dans le scope où
     * la variable a été déclarée.
     *
     * @param declaredKey la clé (UnknownType)
     * @param updatedType le nouveau type
     */
    private void applySubstitutionToScope(UnknownType declaredKey, Type updatedType) {
        // Parcourt les scopes du plus proche au plus éloigné
        for (int i = typeScopes.size() - 1; i >= 0; i--) {
            Map<UnknownType, Type> scope = typeScopes.get(i);

            // Si la variable est déclarée dans ce scope, applique la substitution
            if (scope.containsKey(declaredKey)) {
                scope.put(declaredKey, updatedType);
                break; // Arrête la recherche après avoir trouvé le scope de déclaration
            }
        }
    }

    /**
     * Cherche une variable dans tous les scopes par son nom.
     *
     * @param variableName nom de la variable
     * @return l'entrée Map.Entry avec la clé UnknownType et sa valeur Type,
     *         ou null si non trouvée
     */
    private Map.Entry<UnknownType, Type> existsInAllScopes(String variableName) {
        // Parcourt les scopes de la pile, du bloc courant au global
        for (int i = typeScopes.size() - 1; i >= 0; i--) {
            Map<UnknownType, Type> currentScope = typeScopes.get(i);
            for (Map.Entry<UnknownType, Type> entry : currentScope.entrySet()) {
                if (entry.getKey().getVarName().equals(variableName)) {
                    return entry; // Retourne l'entrée trouvée
                }
            }
        }
        return null; // Retourne null si la variable n'est pas trouvée
    }

    /**
     * Ajoute une nouvelle variable au scope courant.
     *
     * @param variable     la clé (UnknownType)
     * @param declaredType le type déclaré
     * @param currentScope le scope dans lequel on ajoute la variable
     */
    private void addVariableToScope(UnknownType variable, Type declaredType, Map<UnknownType, Type> currentScope) {
        // Ajoute la variable au scope courant
        currentScope.put(variable, declaredType);

    }


    /**
     * Affiche dans la console le contenu des archives de scopes,
     * pour information ou debug.
     */
    public void printArchivedScopes() {

        for (int i = archivedScopes.size() - 1; i >= 0; i--) {
            System.out.println("Scope archivé niveau " + (archivedScopes.size() - i) + ":");
            Map<UnknownType, Type> scope = archivedScopes.get(i);
            for (Map.Entry<UnknownType, Type> entry : scope.entrySet()) {
                System.out.println("    " + entry.getKey().getVarName() + " -> " + entry.getValue());
            }
        }
    }


    /**
     * Vérifie que l'expression visitée est un bool,
     * sinon lève une exception.
     */
    @Override
    public Type visitNegation(grammarTCLParser.NegationContext ctx) {

        String right_string = ctx.expr().getText(); // soit un bool

        Type right_type = visit(ctx.expr());

        unifyToBool(right_string, right_type);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }
        Type bool = new PrimitiveType(Type.Base.BOOL);
        if (!right_type.equals(bool)) {
            throw new UnsupportedOperationException("cannot compare non bool type");
        }
        return bool;
    }

    /**
     * Vérifie que les deux membres de la comparaison sont des int.
     * Retourne un bool.
     */
    @Override
    public Type visitComparison(grammarTCLParser.ComparisonContext ctx) { // on vérifie que le membre de gauche et droite sont des int

        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        // Unifie les types des membres gauche et droit avec INT si nécessaire
        unifyToInt(left_string, left_type);
        unifyToInt(right_string, right_type);


        Map.Entry<UnknownType, Type> LeftEntry = existsInAllScopes(left_string);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(LeftEntry != null){ // si leftEntry ou RightEntry = null alors ce ne sont pas des variables mais des int donc
            left_type = LeftEntry.getValue(); // donc on ne les remplace avc les valeurs mise à jours
        }
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }
        Type type_int = new PrimitiveType(Type.Base.INT);
        if (!left_type.equals(type_int)) {
            throw new UnsupportedOperationException("le terme de gauche n'est pas un int");
        }
        if (!right_type.equals(type_int)) {
            throw new UnsupportedOperationException("le terme de droite n'est pas un int");
        }

        return new PrimitiveType(Type.Base.BOOL);
    }

    /**
     * Vérifie que les deux membres (gauche et droite) sont des bool, retourne un bool.
     */
    @Override
    public Type visitOr(grammarTCLParser.OrContext ctx) { // on vérifie que les deux termes de gauche et droite sont des bools donc quasiment comme égalité

        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        Type bool = new PrimitiveType(Type.Base.BOOL);
        unifyToBool(left_string, left_type);
        unifyToBool(right_string, right_type);


        Map.Entry<UnknownType, Type> LeftEntry = existsInAllScopes(left_string);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(LeftEntry != null){ // si leftEntry ou RightEntry = null alors ce ne sont pas des variables mais des int donc
            left_type = LeftEntry.getValue(); // donc on ne les remplace avc les valeurs mise à jours
        }
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }
        if (!left_type.equals(bool)) {
            throw new UnsupportedOperationException("le terme de gauche n'est pas un boolean");
        }
        if (!right_type.equals(bool)) {
            throw new UnsupportedOperationException("le terme de droite n'est pas un boolean");
        }
        return new PrimitiveType(Type.Base.BOOL);
    }

    /**
     * Vérifie que l'expression est un int (en forçant si c'est un UnknownType).
     * Retourne un int.
     */
    @Override
    public Type visitOpposite(grammarTCLParser.OppositeContext ctx) { // comme VisitNegation sauf qu'on veut juste que l'expression soit un entier

        String right_string = ctx.expr().getText(); // soit un bool

        Type right_type = visit(ctx.expr());

        unifyToInt(right_string, right_type);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }
        Type type_int = new PrimitiveType(Type.Base.INT);
        if (!right_type.equals(type_int)) {
            throw new UnsupportedOperationException("cannot do opposite on non int type");
        }
        return type_int;
    }

    /**
     * Retourne toujours un type INT pour un littéral entier.
     */
    @Override
    public Type visitInteger(grammarTCLParser.IntegerContext ctx) {
        return new PrimitiveType(Type.Base.INT); // Retourne un type INT
    }

    /**
     * Vérifie l'accès dans un tableau : le premier membre doit être un ArrayType,
     * le deuxième membre (l'indice) doit être un int.
     * Retourne le type des éléments du tableau.
     */
    @Override
    public Type visitTab_access(grammarTCLParser.Tab_accessContext ctx) {


        // Récupération de la variable de gauche (le tableau)
        String left_member = ctx.expr(0).getText();


        // Vérification de l'existence de la variable
        Map.Entry<UnknownType, Type> left_entry = existsInAllScopes(left_member);
        if (left_entry == null) {
            throw new UnsupportedOperationException("La variable " + left_member + " n'a pas été déclarée");
        }

        // Vérification que la variable est bien un tableau
        Type left_type = left_entry.getValue();
        if (!(left_type instanceof ArrayType)) {
            throw new UnsupportedOperationException("La variable " + left_member + " n'est pas un tableau.");
        }

        // Récupération du type des éléments du tableau
        ArrayType arrayType = (ArrayType) left_type;
        Type elementType = arrayType.getTabType();


        // Vérification de l'indice (membre de droite)
        String right_member = ctx.expr(1).getText();


        Type right_type = visit(ctx.expr(1));
        Type type_int = new PrimitiveType(Type.Base.INT);
        if (!right_type.equals(type_int)) {
            throw new UnsupportedOperationException("L'indice du tableau " + right_member + " n'est pas un entier.");
        }

        // Retourne le type des éléments du tableau
        return elementType;
    }


    /**
     * Visite d'une expression placée entre parenthèses, retourne le type
     * de l'expression interne.
     */
    @Override
    public Type visitBrackets(grammarTCLParser.BracketsContext ctx) {
        return visit(ctx.expr());
    }

    /**
     * Visite l'appel d'une fonction, vérifie la cohérence des types de paramètres
     * et retourne le type de retour.
     */
    @Override
    public Type visitCall(grammarTCLParser.CallContext ctx) {


        // Récupération du nom de la fonction
        String functionName = ctx.VAR().getText();
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null.");
        }


        // Recherche du type de la fonction
        FunctionType functionType = findFunctionType(functionName);
        if (functionType == null) {
            throw new UnsupportedOperationException("Function '" + functionName + "' does not exist in the current or parent scopes");
        }



        // Récupération du type de retour et des paramètres attendus
        Type returnType = functionType.getReturnType();
        List<Type> expectedParams = functionType.getArgsTypes();


        // Vérification des paramètres fournis
        List<grammarTCLParser.ExprContext> paramsExpr = ctx.expr();
        if (paramsExpr == null) {
            paramsExpr = new ArrayList<>();
        }


        // Vérification du nombre de paramètres
        if (paramsExpr.size() != expectedParams.size()) {
            throw new IllegalArgumentException("Function call '" + functionName + "' has mismatched parameter count. Expected: " +
                    expectedParams.size() + ", Provided: " + paramsExpr.size());
        }

        // Validation et unification des types des paramètres
        for (int i = 0; i < paramsExpr.size(); i++) {
            Type paramType = visit(paramsExpr.get(i)); // Obtention du type du paramètre fourni
            Type expectedType = functionType.getArgsType(i);



            if (expectedType instanceof UnknownType) {

                Map<UnknownType, Type> unification = ((UnknownType) expectedType).unify(paramType);

                for (Map.Entry<UnknownType, Type> entry : unification.entrySet()) {
                    propagateType(entry.getKey(), entry.getValue());
                }

                // Force la mise à jour immédiate de expectedType
                expectedType = expectedType.substituteAll(unification);
                functionType.getArgsTypes().set(i, expectedType);
            }

            // Vérification après unification

            if (!expectedType.equals(paramType)) {
                throw new IllegalArgumentException("Type mismatch for parameter " + (i + 1) + " in function call '" + functionName +
                        "'. Expected: " + expectedType + ", Provided: " + paramType);
            }
        }


        return returnType; // Retourne le type de retour de la fonction
    }


    /**
     * Retourne toujours un type BOOL pour un littéral booléen.
     */
    @Override
    public Type visitBoolean(grammarTCLParser.BooleanContext ctx) {
        return new PrimitiveType(Type.Base.BOOL);
    }

    /**
     * Vérifie que les deux membres (gauche et droite) sont des bool, retourne un bool.
     * Même logique que pour OR.
     */
    @Override
    public Type visitAnd(grammarTCLParser.AndContext ctx) { // se comporte comme un OR


        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        Type bool = new PrimitiveType(Type.Base.BOOL);
        unifyToBool(left_string, left_type);
        unifyToBool(right_string, right_type);


        Map.Entry<UnknownType, Type> LeftEntry = existsInAllScopes(left_string);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(LeftEntry != null){ // si leftEntry ou RightEntry = null alors ce ne sont pas des variables mais des int donc
            left_type = LeftEntry.getValue(); // donc on ne les remplace avc les valeurs mise à jours
        }
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }
        if (!left_type.equals(bool) & !right_type.equals(bool)) {
            throw new UnsupportedOperationException("le terme de gauche n'est pas un boolean");
        }
        return new PrimitiveType(Type.Base.BOOL);
    }

    /**
     * Visite une variable, retourne son type si elle a déjà été déclarée,
     * sinon lève une exception.
     */
    @Override
    public Type visitVariable(grammarTCLParser.VariableContext ctx) {
        // Récupère le nom de la variable depuis le contexte
        String variableName = ctx.VAR().getText();


        // Utilise existsInAllScopes pour chercher la variable
        Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);

        // Si la variable n'est pas déclarée
        if (foundEntry == null) {
            throw new IllegalArgumentException("Variable non déclarée : " + variableName);
        }

        UnknownType declaredKey = foundEntry.getKey();
        Type declaredType = foundEntry.getValue();

        // Affiche les informations trouvées



        // Retourne le type trouvé ou la clé si le type est null
        return declaredType != null ? declaredType : declaredKey;
    }

    /**
     * Vérifie que l'opération de multiplication est faite entre deux int,
     * sinon lève une exception.
     * Retourne un int si tout va bien.
     */
    @Override
    public Type visitMultiplication(grammarTCLParser.MultiplicationContext ctx) {

        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        // Unifie les types des membres gauche et droit avec INT si nécessaire

        unifyToInt(left_string, left_type);
        unifyToInt(right_string, right_type);


        Map.Entry<UnknownType, Type> LeftEntry = existsInAllScopes(left_string);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(LeftEntry != null){ // si leftEntry ou RightEntry = null alors ce ne sont pas des variables mais des int donc
            left_type = LeftEntry.getValue(); // donc on ne les remplace avc les valeurs mise à jours
        }
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }

        if(!left_type.equals(right_type)){
            throw new UnsupportedOperationException("vous Multiplier des expr de différents type");
        }
        if (left_type.equals(new PrimitiveType(Type.Base.INT))) {

            return new PrimitiveType(Type.Base.INT);
        } else {
            throw new UnsupportedOperationException("La Multiplication est uniquement supportée pour des types INT.");
        }
    }

    /**
     * Vérifie que les deux membres (gauche et droite) de l'égalité
     * sont du même type. Retourne un bool.
     */
    @Override
    public Type visitEquality(grammarTCLParser.EqualityContext ctx) { //l'unification fonctionne comme l'assignement

        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();


        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));


        if (left_type instanceof UnknownType) {
            // Recherche de la variable dans tous les scopes
            Map.Entry<UnknownType, Type> leftEntry = existsInAllScopes(left_string);
            // Si la variable n'est pas déclarée
            if (leftEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + left_string);
            }
        }
        if (right_type instanceof UnknownType) {
            // Recherche de la variable dans tous les scopes
            Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
            // Si la variable n'est pas déclarée
            if (RightEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + left_string);
            }
        }
        if (right_type instanceof UnknownType && left_type instanceof UnknownType) {
            Map.Entry<UnknownType, Type> leftEntry = existsInAllScopes(left_string);
            Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);

            linkUnknownTypes(leftEntry.getKey(), leftEntry.getValue(), right_string, RightEntry.getValue());
        }




        if (!left_type.equals(right_type)) {
            throw new UnsupportedOperationException("vous comparer des expr de différents type");
        }
        return new PrimitiveType(Type.Base.BOOL);
    }

    /**
     * Vérifie que tous les éléments sont du même type, puis
     * retourne un ArrayType de ce type.
     */
    @Override
    public Type visitTab_initialization(grammarTCLParser.Tab_initializationContext ctx) {

        String variableName = ctx.expr(0).getText();

        Type previous_type = visit(ctx.expr(0));
        for (var expr : ctx.expr()) { // on parcourt la liste et on compare chaque type de nombre entre eux.

            Type type_valeurs = visit(expr);


            if (!previous_type.equals(type_valeurs)) {
                throw new UnsupportedOperationException("Votre tableau a différents types");
            }
        }
        Type Array_type = new ArrayType(previous_type);
        return Array_type; // si la visite s'est bien passé alors tous les elements du tab sont du même type que le premier
    }

    /**
     * Vérifie que l'addition est faite entre deux int,
     * sinon lève une exception. Retourne un int si OK.
     */
    @Override
    public Type visitAddition(grammarTCLParser.AdditionContext ctx) {
        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        // Unifie les types des membres gauche et droit avec INT si nécessaire
        unifyToInt(left_string, left_type);
        unifyToInt(right_string, right_type);


        Map.Entry<UnknownType, Type> LeftEntry = existsInAllScopes(left_string);
        Map.Entry<UnknownType, Type> RightEntry = existsInAllScopes(right_string);
        if(LeftEntry != null){ // si leftEntry ou RightEntry = null alors ce ne sont pas des variables mais des int donc
            left_type = LeftEntry.getValue(); // donc on ne les remplace avc les valeurs mise à jours
        }
        if(RightEntry != null){
            right_type = RightEntry.getValue();
        }

        if(!left_type.equals(right_type)){
            throw new UnsupportedOperationException("vous additionner des expr de différents type");
        }
        if (left_type.equals(new PrimitiveType(Type.Base.INT))) {

            return new PrimitiveType(Type.Base.INT);
        } else {
            throw new UnsupportedOperationException("L'addition est uniquement supportée pour des types INT.");
        }

    }

    /**
     * Retourne un type de base (int, bool, auto) en fonction du contenu
     * du contexte. Lance une exception si le type est inconnu.
     */
    @Override
    public Type visitBase_type(grammarTCLParser.Base_typeContext ctx) {
        // Récupère le texte du type de base
        String baseType = ctx.getText();

        // Retourne le type correspondant
        switch (baseType) {
            case "int":
                return new PrimitiveType(Type.Base.INT);
            case "bool":
                return new PrimitiveType(Type.Base.BOOL);
            case "auto":
                return new UnknownType();
            default:
                throw new IllegalArgumentException("Type de base inconnu : " + baseType);
        }
    }


    /**
     * Retourne un ArrayType dont le contenu est le type retourné par {@link #visitBase_type}
     * ou un autre type (si c'est un tableau de tableau, etc.).
     */
    @Override
    public Type visitTab_type(grammarTCLParser.Tab_typeContext ctx) {

        String left_string = ctx.type().getText();

        Type tab_type = visit(ctx.type());
        Type Array_type = new ArrayType(tab_type);
        return Array_type;
    }

    /**
     * Visite une déclaration de variable (type + nom). Si une initialisation
     * est présente, on appelle visitAssignment pour gérer l'affectation.
     */
    @Override
    public Type visitDeclaration(grammarTCLParser.DeclarationContext ctx) {
        String variableName = ctx.VAR().getText();
        UnknownType variable = new UnknownType(ctx.VAR());


        // Récupère le scope actuel (le sommet de la pile)
        Map<UnknownType, Type> currentScope = typeScopes.peek();

        // Vérifie si une variable avec le même nom existe déjà dans le scope courant
        if (VarExistsInCurrentScope(variableName, currentScope)) {
            throw new IllegalArgumentException("Variable déjà déclarée dans ce bloc : " + variableName);
        }

        // Ajout de la déclaration au scope courant
        Type declaredType = visit(ctx.type());
        addVariableToScope(variable, declaredType, currentScope);
        // Si une initialisation est présente, visite l'expression assignée
        if (ctx.expr() != null) {

            //Type expressionType = visit(ctx.expr());


            grammarTCLParser.AssignmentContext assignmentContext = new grammarTCLParser.AssignmentContext(ctx);
            assignmentContext.children = new ArrayList<>(); // Initialise manuellement les enfants
            assignmentContext.addChild(ctx.VAR());
            assignmentContext.addChild(ctx.ASSIGN());
            assignmentContext.addChild(ctx.expr());

            // Appeler visitAssignment pour gérer l'initialisation
            Type expressionType = visitAssignment(assignmentContext);


            // Vérifie la compatibilité des types entre la déclaration et l'expression assignée
            if (!declaredType.equals(expressionType)) {
                throw new IllegalArgumentException("Type mismatch in declaration: " + declaredType + " != " + expressionType);
            }
        }
        // Affiche l'état actuel de la pile des scopes

        return declaredType;
    }

    /**
     * Visite l'instruction print, vérifie que la variable est déclarée.
     */
    @Override
    public Type visitPrint(grammarTCLParser.PrintContext ctx) { // vérifié que la variable a été déclaré (donc parcourir le scope)

        String variableName = ctx.VAR().getText();

        if(existsInAllScopes(variableName)==null){
            throw new UnsupportedOperationException("Variable '" + variableName + "' has not been declared.");
        };
        return null;
    }

    /**
     * Gère l'affectation d'une variable (membre gauche) avec une expression (membre droit).
     * Vérifie la cohérence des types, puis propage l'unification si nécessaire.
     */
    @Override
    public Type visitAssignment(grammarTCLParser.AssignmentContext ctx) {


        String variableName = ctx.VAR().getText();
        Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);
        if (foundEntry == null) {
            throw new IllegalArgumentException("Variable non déclarée : " + variableName);
        }
        UnknownType declaredKey = foundEntry.getKey();
        Type declaredType = foundEntry.getValue();

        grammarTCLParser.ExprContext rightExpr = ctx.expr(0);
        Type rightType = visit(rightExpr);

        // Vérification si le type droit est un UnknownType et unification correcte
        if (declaredType instanceof UnknownType && rightType instanceof PrimitiveType) {

            propagateType(declaredKey, rightType);
            declaredType = rightType;
        } else if (declaredType instanceof PrimitiveType && rightType instanceof UnknownType) {

            propagateType((UnknownType) rightType, declaredType);
            rightType = declaredType;
        }

        if (!(declaredType instanceof UnknownType) && !(rightType instanceof UnknownType)) {
            if (!declaredType.equals(rightType)) {
                throw new IllegalArgumentException(
                        "Type mismatch: " + declaredType + " != " + rightType
                );
            }
        }

        // Lien entre deux `UnknownType`
        linkUnknownTypes(declaredKey, declaredType, rightExpr.getText(), rightType);


        return declaredType;
    }

    /**
     * Visite un bloc de code : crée un nouveau scope, visite chaque instruction,
     * puis archive le scope une fois terminé.
     */
    @Override
    public Type visitBlock(grammarTCLParser.BlockContext ctx) {


        // Ajouter un nouveau scope à la pile
        typeScopes.push(new HashMap<>());


        try {
            // Visite des instructions à l'intérieur du bloc
            for (var instr : ctx.instr()) {

                visit(instr);
            }
        } finally {
            // Archiver le scope courant avant de le supprimer
            Map<UnknownType, Type> exitingScope = typeScopes.pop();
            archivedScopes.push(exitingScope);


        }

        return null; // Un bloc ne retourne pas de type spécifique
    }

    /**
     * Visite un if : vérifie que la condition est un bool,
     * visite ensuite le bloc "then" et éventuellement le bloc "else".
     */
    @Override
    public Type visitIf(grammarTCLParser.IfContext ctx) { // on peut inférer que le auto est un bool


        // Visite et vérifie le type de la condition

        String condition_name = ctx.expr().getText();
        Type conditionType = visit(ctx.expr());
        if (conditionType instanceof UnknownType) {
            unifyToBool(condition_name, conditionType);
        }
        Map.Entry<UnknownType, Type> Condition_Entry = existsInAllScopes(condition_name);
        if(Condition_Entry != null){
            conditionType = Condition_Entry.getValue();
        }
        // La condition doit être de type BOOL
        if (!conditionType.equals(new PrimitiveType(Type.Base.BOOL))) {
            throw new IllegalArgumentException("La condition de l'IF doit être de type BOOL. Trouvé : " + conditionType);
        }



        // Visite le bloc d'instructions du IF

        visit(ctx.instr(0)); // Premier bloc d'instructions après la condition

        // Visite le bloc ELSE s'il existe
        if (ctx.ELSE() != null) {

            visit(ctx.instr(1)); // Deuxième bloc d'instructions (bloc ELSE)
        }


        return null; // Le IF ne retourne pas de type particulier
    }

    /**
     * Visite un while : vérifie que la condition est de type bool,
     * puis visite l'instruction à l'intérieur de la boucle.
     */
    @Override
    public Type visitWhile(grammarTCLParser.WhileContext ctx) { //semblable au VisitIf

        Type conditionType = visit(ctx.expr());
        // La condition doit être de type BOOL
        if (!conditionType.equals(new PrimitiveType(Type.Base.BOOL))) {// il faut vérifier que l'expr est un bool
            throw new IllegalArgumentException("La condition du while doit être de type BOOL. Trouvé : " + conditionType);
        }
        visit(ctx.instr());// on visite l'instruction pour typer à l'intérieur de l'instruction
        return null; // le while ne retourne rien
    }

    /**
     * Visite un for : vérifie la condition (doit être bool),
     * et visite les 3 instructions (initialisation, incrémentation, et le corps).
     */
    @Override
    public Type visitFor(grammarTCLParser.ForContext ctx) { //selon la grammaire : 3 instr et 1 expr, on va ts les visiter et faire comme dans visitWhile et VisitIf

        visit(ctx.instr(0));
        String condition_name = ctx.expr().getText(); //comme dans l'inférence de la condition avec le if
        Type conditionType = visit(ctx.expr());
        if (conditionType instanceof UnknownType) {
            unifyToBool(condition_name, conditionType);
        }
        Map.Entry<UnknownType, Type> Condition_Entry = existsInAllScopes(condition_name);
        if(Condition_Entry != null){
            conditionType = Condition_Entry.getValue();
        }
        // La condition doit être de type BOOL
        if (!conditionType.equals(new PrimitiveType(Type.Base.BOOL))) {// il faut vérifier que l'expr est un bool
            throw new IllegalArgumentException("La condition du FOR doit être de type BOOL. Trouvé : " + conditionType);
        }
        visit(ctx.instr(1));
        visit(ctx.instr(2));
        return null; // comme visitIf et VisitWhile
    }

    /**
     * Visite un return : capture le type de l'expression retournée.
     *
     * @return le type de l'expression retournée
     */
    @Override
    public Type visitReturn(grammarTCLParser.ReturnContext ctx) {
        return visit(ctx.expr()); // Retourne le type
    }

    /**
     * Visite le corps d'une fonction (core_fct),
     * c'est-à-dire les instructions suivies éventuellement d'un return.
     *
     * @return le type de retour si un return est présent, sinon null
     */
    @Override
    public Type visitCore_fct(grammarTCLParser.Core_fctContext ctx) {


        // Parcourt et visite toutes les instructions
        for (var instr : ctx.instr()) {

            visit(instr); // Visite chaque instruction (y compris les déclarations)
        }

        // Visite l'expression de retour si elle existe
        if (ctx.RETURN() != null) {


            // Récupère le type de retour
            grammarTCLParser.ExprContext test = ctx.expr();
            Type returnType = visit(ctx.expr());

            // Ajoute le typeScope actuel à l'archive
            if (!typeScopes.isEmpty()) {
                Map<UnknownType, Type> currentScope = new HashMap<>(typeScopes.peek());
                archivedScopes.push(currentScope);
            }

            return returnType; // Retourne le type de retour
        }

        return null; // Retourne null si aucune expression RETURN n'est présente
    }

    /**
     * Visite la déclaration d'une fonction,
     * y compris la création d'un scope pour ses paramètres et son corps.
     *
     * @return le FunctionType correspondant à la fonction.
     */
    @Override
    public Type visitDecl_fct(grammarTCLParser.Decl_fctContext ctx) {


        // Récupération du type de retour et du nom de la fonction
        Type returnType = visit(ctx.type(0));
        String functionName = ctx.VAR(0).getText();


        // Analyse des paramètres
        ArrayList<Type> parametersType = new ArrayList<>();
        if (ctx.type().size() > 1) {
            for (int i = 1; i < ctx.type().size(); i++) {
                parametersType.add(visit(ctx.type(i)));
            }
        }

        // Création de l'objet représentant la fonction
        FunctionType functionType = new FunctionType(returnType, parametersType);

        // Vérifie si la fonction existe déjà dans le scope actuel
        Map<UnknownType, Type> currentScope = typeScopes.peek();
        if (FunctionExistsInCurrentScope(functionName, currentScope)) {
            throw new UnsupportedOperationException("La fonction " + functionName + " est déjà déclarée.");
        }

        // Ajoute la fonction à la table des types globale
        UnknownType functionKey = new UnknownType(ctx.VAR(0));
        addVariableToScope(functionKey, functionType, currentScope);

        // **Vérifier si un scope local existe déjà**
        boolean newScopeCreated = typeScopes.size() == 1; // On est dans le scope global

        if (newScopeCreated) {
            typeScopes.push(new HashMap<>());

        }

        // Déclaration des paramètres dans le scope local
        for (int i = 1; i < ctx.type().size(); i++) {
            grammarTCLParser.InstrContext instrCtx = new grammarTCLParser.InstrContext();
            grammarTCLParser.DeclarationContext declarationCtx = new grammarTCLParser.DeclarationContext(instrCtx);
            declarationCtx.children = new ArrayList<>();
            declarationCtx.addChild(ctx.type(i));
            declarationCtx.addChild(ctx.VAR(i));

            visitDeclaration(declarationCtx);
        }

        // Visite du corps de la fonction
        Type coreReturnType = visit(ctx.core_fct());

        // Si le type de retour est `auto`, il doit être remplacé par le type réellement retourné
        if (returnType instanceof UnknownType) {


            // Unification avec le type réellement retourné
            Map<UnknownType, Type> unification = ((UnknownType) returnType).unify(coreReturnType);
            for (Map.Entry<UnknownType, Type> entry : unification.entrySet()) {
                propagateType(entry.getKey(), entry.getValue());
            }

            // Mise à jour du type de retour
            returnType = find((UnknownType) returnType);

        }

        // FORCER L'UNIFICATION DU TYPE DE RETOUR
        if (returnType instanceof UnknownType && coreReturnType instanceof PrimitiveType) {

            propagateType((UnknownType) returnType, coreReturnType);
            returnType = coreReturnType;
        }

        // Si le type de retour est un `UnknownType`, on essaie de l'unifier avec le type retourné
        if (returnType instanceof UnknownType) {

            union((UnknownType) returnType, (UnknownType) coreReturnType);
            returnType = find((UnknownType) returnType); // Mise à jour avec la racine unifiée
            coreReturnType = find((UnknownType) coreReturnType);
        }

        // Vérification après unification
        if (!coreReturnType.equals(returnType)) {
            throw new IllegalArgumentException(
                    "Type de retour incompatible pour la fonction '" + functionName +
                            "'. Attendu : " + returnType + ", Trouvé : " + coreReturnType
            );
        }

        // Mise à jour de la déclaration de la fonction avec le type correct
        currentScope.put(functionKey, new FunctionType(returnType, parametersType));

        // Archivage du scope seulement si créé
        if (newScopeCreated) {
            Map<UnknownType, Type> localScope = typeScopes.pop();

        }

        return functionType;
    }

    /**
     * Visite le noeud principal (main) : parcourt les fonctions déclarées,
     * puis le corps principal s'il existe.
     */
    @Override
    public Type visitMain(grammarTCLParser.MainContext ctx) {


        // Parcourt les déclarations de fonctions
        for (var declFct : ctx.decl_fct()) {

            visit(declFct);
        }

        // Visite le cœur de la fonction principale
        if (ctx.core_fct() != null) {

            visit(ctx.core_fct());
        }
        return null;
    }
}
