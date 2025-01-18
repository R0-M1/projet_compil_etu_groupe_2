import java.util.*;
// Import des types utilisés
import Type.ArrayType;

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

import Type.Type;
import Type.UnknownType;
import Type.PrimitiveType;
import Type.FunctionType;
import org.antlr.v4.runtime.tree.ParseTree;


public class TyperVisitor extends AbstractParseTreeVisitor<Type> implements grammarTCLVisitor<Type> {

    private Map<UnknownType,Type> types = new HashMap<UnknownType,Type>();

    private Stack<Map<UnknownType, Type>> typeScopes = new Stack<>();
    private Stack<Map<UnknownType, Type>> archivedScopes = new Stack<>();
    // HashMap supplémentaire pour conserver les relations entre UnknownType
    private Map<UnknownType, UnknownType> autoLinkMap = new HashMap<>();


    public TyperVisitor() {
        typeScopes.push(new HashMap<>()); // Scope global
        archivedScopes.push(new HashMap<>()); // Scope global
    }
    private UnknownType find(UnknownType type) {
        if (!autoLinkMap.containsKey(type)) {
            return type; // Si aucun lien, retourne lui-même (racine de son propre groupe)
        }
        // Compression de chemin pour optimisation
        UnknownType root = find(autoLinkMap.get(type)); // Trouve la racine récursivement
        autoLinkMap.put(type, root); // Compression de chemin : met à jour pour pointer directement sur la racine
        return root;
    }


    // Fonction pour relier deux `UnknownType`
    private void union(UnknownType type1, UnknownType type2) {
        UnknownType root1 = find(type1);
        UnknownType root2 = find(type2);
        if (!root1.equals(root2)) {
            autoLinkMap.put(root1, root2); // Relie le premier représentant au second
            System.out.println("Union entre " + root1 + " et " + root2);
        }
    }
    /**
     * Établit un lien entre deux `UnknownType` si les deux sont effectivement des `UnknownType`.
     *
     * @param declaredKey   Le `UnknownType` du côté gauche de l'opération.
     * @param declaredType  Le type déclaré correspondant au `declaredKey`.
     * @param rightVariableName Le nom de la variable ou expression à droite de l'opération.
     * @param rightType     Le type de l'expression ou variable à droite.
     */
    private void linkUnknownTypes(UnknownType declaredKey, Type declaredType, String rightVariableName, Type rightType) {
        // Vérifie que les deux types sont des UnknownType
        if (declaredType instanceof UnknownType && rightType instanceof UnknownType) {
            System.out.println("Tentative de liaison entre deux UnknownType.");

            // Recherche de la clé de la variable à droite dans les scopes
            Map.Entry<UnknownType, Type> foundRightEntry = existsInAllScopes(rightVariableName);
            if (foundRightEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + rightVariableName);
            }

            UnknownType rightKey = foundRightEntry.getKey();
            System.out.println("Établissement d'une relation entre " + declaredKey + " et " + rightKey);

            // Établit la relation via union
            union(declaredKey, rightKey);
        }
    }

    private void propagateType(UnknownType type, Type realType) {
        // Trouve la racine canonique
        UnknownType root = find(type);
        System.out.println("propagate type");
        // Parcourt toutes les variables liées à cette racine
        for (Map.Entry<UnknownType, UnknownType> entry : autoLinkMap.entrySet()) {
            System.out.println("entry :" + entry.getKey() + " " + entry.getValue());
            if (find(entry.getKey()).equals(root)) {
                // Met à jour le type dans tous les scopes concernés
                applySubstitutionToScope(entry.getKey(), realType);
            }
        }

        // Met également à jour la racine elle-même
        applySubstitutionToScope(root, realType);
    }
    /**
     * Unifie un UnknownType avec le type INT et propage les changements dans tous les scopes.
     *
     * @param variableName Nom de la variable à unifier.
     * @param variableType Type actuel de la variable.
     */
    private void unifyToInt(String variableName, Type variableType) {

        // Vérifie si le type est un UnknownType
        if (variableType instanceof UnknownType) {
            System.out.println("Unify to int called " + variableName);
            // Recherche la clé de la variable dans tous les scopes
            Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);
            if (foundEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + variableName);
            }

            UnknownType key = foundEntry.getKey();
            System.out.println("Unification de " + variableName + " (clé : " + key + ") avec INT.");

            // Effectue l'unification avec INT
            Map<UnknownType, Type> unification = key.unify(new PrimitiveType(Type.Base.INT));
            System.out.println("Résultat de l'unification : " + unification);

            // Propage les changements
            for (Map.Entry<UnknownType, Type> entry : unification.entrySet()) {
                propagateType(entry.getKey(), entry.getValue());
            }
        }
    }
    /**
     * Unifie un UnknownType avec le type BOOL et propage les changements dans tous les scopes.
     *
     * @param variableName Nom de la variable à unifier.
     * @param variableType Type actuel de la variable.
     */
    private void unifyToBool(String variableName, Type variableType) {
        // Vérifie si le type est un UnknownType
        if (variableType instanceof UnknownType) {
            System.out.println("Unify to bool called for " + variableName);

            // Recherche la clé de la variable dans tous les scopes
            Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);
            if (foundEntry == null) {
                throw new IllegalArgumentException("Variable non déclarée : " + variableName);
            }

            UnknownType key = foundEntry.getKey();
            System.out.println("Unification de " + variableName + " (clé : " + key + ") avec BOOL.");

            // Effectue l'unification avec BOOL
            Map<UnknownType, Type> unification = key.unify(new PrimitiveType(Type.Base.BOOL));
            System.out.println("Résultat de l'unification : " + unification);

            // Propage les changements
            for (Map.Entry<UnknownType, Type> entry : unification.entrySet()) {
                propagateType(entry.getKey(), entry.getValue());
            }
        }
    }


    public Stack<Map<UnknownType, Type>> getTypeScopes() {
        return typeScopes;
    }
    public Stack<Map<UnknownType, Type>> getarchivedScopes() {
        return archivedScopes;
    }

    public Map<UnknownType, Type> getTypes() {
        return types;
    }

    private boolean VarExistsInCurrentScope(String variableName, Map<UnknownType, Type> currentScope) {
        for (UnknownType key : currentScope.keySet()) {
            if (key.getVarName().equals(variableName)) {
                return true; // La variable existe déjà dans le scope courant
            }
        }
        return false; // La variable n'existe pas dans le scope courant
    }
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
     * Applique les substitutions dans le scope où la variable a été déclarée.
     * @param declaredKey La clé représentant la variable (UnknownType).
     * @param updatedType Le type unifié à appliquer.
     */
    private void applySubstitutionToScope(UnknownType declaredKey, Type updatedType) {
        // Parcourt les scopes du plus proche au plus éloigné
        for (int i = typeScopes.size() - 1; i >= 0; i--) {
            Map<UnknownType, Type> scope = typeScopes.get(i);

            // Si la variable est déclarée dans ce scope, applique la substitution
            if (scope.containsKey(declaredKey)) {
                System.out.println("Substitution appliquée dans le scope " + i + " : " +
                        declaredKey + " -> " + updatedType);
                scope.put(declaredKey, updatedType);
                break; // Arrête la recherche après avoir trouvé le scope de déclaration
            }
        }
    }

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

    private void addVariableToScope(UnknownType variable, Type declaredType, Map<UnknownType, Type> currentScope) {
        // Ajoute la variable au scope courant
        currentScope.put(variable, declaredType);
        System.out.println("Ajout à la table des types dans le scope courant : " + variable + " -> " + declaredType);
    }


    public void printArchivedScopes() {
        System.out.println("Archives des scopes :");
        for (int i = archivedScopes.size() - 1; i >= 0; i--) {
            System.out.println("Scope archivé niveau " + (archivedScopes.size() - i) + ":");
            Map<UnknownType, Type> scope = archivedScopes.get(i);
            for (Map.Entry<UnknownType, Type> entry : scope.entrySet()) {
                System.out.println("    " + entry.getKey().getVarName() + " -> " + entry.getValue());
            }
        }
    }


    @Override
    public Type visitNegation(grammarTCLParser.NegationContext ctx) {
        System.out.println("Visite negation"); // Dans visitNegation, il faut juste que le membre de droite soit un bool
        String right_string = ctx.expr().getText(); // soit un bool
        System.out.println("Déclaration droite : " + right_string);
        Type right_type = visit(ctx.expr());
        System.out.println("type droit trouvée : " + right_type);
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

    @Override
    public Type visitComparison(grammarTCLParser.ComparisonContext ctx) { // on vérifie que le membre de gauche et droite sont des int
        System.out.println("visitComparison");
        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        // Unifie les types des membres gauche et droit avec INT si nécessaire
        System.out.println("right_type :  " + right_type);
        if (right_type instanceof UnknownType) {
            System.out.println("bool instance de unknownType");
        }
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

    @Override
    public Type visitOr(grammarTCLParser.OrContext ctx) { // on vérifie que les deux termes de gauche et droite sont des bools donc quasiment comme égalité
        System.out.println("visitOr");
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

    @Override
    public Type visitOpposite(grammarTCLParser.OppositeContext ctx) { // comme VisitNegation sauf qu'on veut juste que l'expression soit un entier
        System.out.println("visitOpposite");
        String right_string = ctx.expr().getText(); // soit un bool
        System.out.println("Déclaration droite : " + right_string);
        Type right_type = visit(ctx.expr());
        System.out.println("type droit trouvée : " + right_type);
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

    @Override
    public Type visitInteger(grammarTCLParser.IntegerContext ctx) {
        return new PrimitiveType(Type.Base.INT); // Retourne un type INT
    }

    @Override
    public Type visitTab_access(grammarTCLParser.Tab_accessContext ctx) {
        System.out.println("visitTabAccess");

        // Récupération de la variable de gauche (le tableau)
        String left_member = ctx.expr(0).getText();
        System.out.println("Variable tableau : " + left_member);

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
        System.out.println("Type des éléments du tableau : " + elementType);

        // Vérification de l'indice (membre de droite)
        String right_member = ctx.expr(1).getText();
        System.out.println("Indice : " + right_member);

        Type right_type = visit(ctx.expr(1));
        Type type_int = new PrimitiveType(Type.Base.INT);
        if (!right_type.equals(type_int)) {
            throw new UnsupportedOperationException("L'indice du tableau " + right_member + " n'est pas un entier.");
        }

        // Retourne le type des éléments du tableau
        return elementType;
    }


    @Override
    public Type visitBrackets(grammarTCLParser.BracketsContext ctx) {
        System.out.println("visitBrackets");
        Type inside_brackets = visit(ctx.expr());
        return inside_brackets;
    }

    @Override
    public Type visitCall(grammarTCLParser.CallContext ctx) {
        System.out.println("visitCall");

        // Retrieve the function name
        String functionName = ctx.VAR().getText();
        if (functionName == null) {
            throw new IllegalArgumentException("Function name cannot be null.");
        }
        System.out.println("Function name: " + functionName);

        // Find the function type
        FunctionType functionType = findFunctionType(functionName);
        if (functionType == null) {
            throw new UnsupportedOperationException("Function '" + functionName + "' does not exist in the current or parent scopes");
        }

        System.out.println("Function type: " + functionType);

        // Get the return type and expected parameters of the function
        Type returnType = functionType.getReturnType();
        List<Type> expectedParams = functionType.getArgsTypes();
        System.out.println("Expected parameters: " + expectedParams);

        // Validate provided parameters
        List<grammarTCLParser.ExprContext> paramsExpr = ctx.expr();
        if (paramsExpr == null) {
            paramsExpr = new ArrayList<>(); // Handle null case with an empty list
        }
        System.out.println("Provided parameters: " + paramsExpr);

        // Check parameter count
        if (paramsExpr.size() != expectedParams.size()) {
            throw new IllegalArgumentException("Function call '" + functionName + "' has mismatched parameter count. Expected: " +
                    expectedParams.size() + ", Provided: " + paramsExpr.size());
        }

        // Validate parameter types
        for (int i = 0; i < paramsExpr.size(); i++) {
            Type paramType = visit(paramsExpr.get(i)); // Get type of the provided parameter
            Type expectedType = functionType.getArgsType(i);

            System.out.println("Expected parameter " + (i + 1) + " type: " + expectedType + ", Provided type: " + paramType);

            if (!paramType.equals(expectedType)) {
                throw new IllegalArgumentException("Type mismatch for parameter " + (i + 1) + " in function call '" + functionName +
                        "'. Expected: " + expectedType + ", Provided: " + paramType);
            }
        }

        System.out.println("Function call '" + functionName + "' is valid.");
        return returnType; // Return the function's return type
    }


    @Override
    public Type visitBoolean(grammarTCLParser.BooleanContext ctx) {
        return new PrimitiveType(Type.Base.BOOL);
    }

    @Override
    public Type visitAnd(grammarTCLParser.AndContext ctx) { // se comporte comme un OR
        System.out.println("visitAnd");

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

    @Override
    public Type visitVariable(grammarTCLParser.VariableContext ctx) {
        // Récupère le nom de la variable depuis le contexte
        String variableName = ctx.VAR().getText();
        System.out.println("Recherche de la variable par nom : " + variableName);

        // Utilise existsInAllScopes pour chercher la variable
        Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);

        // Si la variable n'est pas déclarée
        if (foundEntry == null) {
            throw new IllegalArgumentException("Variable non déclarée : " + variableName);
        }

        UnknownType declaredKey = foundEntry.getKey();
        Type declaredType = foundEntry.getValue();

        // Affiche les informations trouvées
        System.out.println("Clé trouvée : " + declaredKey);
        System.out.println("Type associé : " + declaredType);

        // Retourne le type trouvé ou la clé si le type est null
        return declaredType != null ? declaredType : declaredKey;
    }

    @Override
    public Type visitMultiplication(grammarTCLParser.MultiplicationContext ctx) {
        System.out.println("Visite Multiplication");
        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        // Unifie les types des membres gauche et droit avec INT si nécessaire
        System.out.println("right_type :  " + right_type);
        if (right_type instanceof UnknownType) {
            System.out.println("bool instance de unknownType");
        }
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
            System.out.println("Multiplication réussie entre deux INT.");
            return new PrimitiveType(Type.Base.INT);
        } else {
            throw new UnsupportedOperationException("La Multiplication est uniquement supportée pour des types INT.");
        }
    }

    @Override
    public Type visitEquality(grammarTCLParser.EqualityContext ctx) { //l'unification fonctionne comme l'assignement
        System.out.println("visitEquality");
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();
        System.out.println("Déclaration gauche : " + left_string);
        System.out.println("Déclaration droite : " + right_string);
        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        System.out.println("type gauche trouvée : " + left_type);
        System.out.println("type droit trouvée : " + right_type);
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

    @Override
    public Type visitTab_initialization(grammarTCLParser.Tab_initializationContext ctx) {
        System.out.println("visitTab_initialization");
        String variableName = ctx.expr(0).getText();
        System.out.println(variableName);
        Type previous_type = visit(ctx.expr(0));
        for (var expr : ctx.expr()) { // on parcourt la liste et on compare chaque type de nombre entre eux.
            System.out.println("Visite de l'expression : " + expr.getText());
            Type type_valeurs = visit(expr);
            System.out.println("type_valeurs : " + type_valeurs);
            System.out.println(type_valeurs);
            if (!previous_type.equals(type_valeurs)) {
                throw new UnsupportedOperationException("Votre tableau a différents types");
            }
        }
        Type Array_type = new ArrayType(previous_type);
        return Array_type; // si la visite s'est bien passé alors tous les elements du tab sont du même type que le premier
    }

    @Override
    public Type visitAddition(grammarTCLParser.AdditionContext ctx) {
        System.out.println("Visite addition");
        Type left_type = visit(ctx.expr(0));
        Type right_type = visit(ctx.expr(1));
        String left_string = ctx.expr(0).getText();
        String right_string = ctx.expr(1).getText();

        // Unifie les types des membres gauche et droit avec INT si nécessaire
        System.out.println("right_type :  " + right_type);
        if (right_type instanceof UnknownType) {
            System.out.println("bool instance de unknownType");
        }
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
            System.out.println("Addition réussie entre deux INT.");
            return new PrimitiveType(Type.Base.INT);
        } else {
            throw new UnsupportedOperationException("L'addition est uniquement supportée pour des types INT.");
        }

    }

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


    @Override
    public Type visitTab_type(grammarTCLParser.Tab_typeContext ctx) {
        System.out.println("visitTab_type");
        String left_string = ctx.type().getText();
        System.out.println(left_string);
        Type tab_type = visit(ctx.type());
        Type Array_type = new ArrayType(tab_type);
        return Array_type;
    }

    @Override
    public Type visitDeclaration(grammarTCLParser.DeclarationContext ctx) {
        String variableName = ctx.VAR().getText();
        UnknownType variable = new UnknownType(ctx.VAR());
        System.out.println("Déclaration trouvée : " + variable);

        // Récupère le scope actuel (le sommet de la pile)
        Map<UnknownType, Type> currentScope = typeScopes.peek();

        // Vérifie si une variable avec le même nom existe déjà dans le scope courant
        if (VarExistsInCurrentScope(variableName, currentScope)) {
            throw new IllegalArgumentException("Variable déjà déclarée dans ce bloc : " + variableName);
        }

        if (VarExistsInParentScopes(variableName)) {
            System.out.println("Une variable avec ce nom existe dans un scope parent : " + variableName);
        }

        // Ajout de la déclaration au scope courant
        Type declaredType = visit(ctx.type());
        addVariableToScope(variable, declaredType, currentScope);
        // Si une initialisation est présente, visite l'expression assignée
        if (ctx.expr() != null) {
            System.out.println("Expression d'initialisation trouvée pour " + variableName + ": " + ctx.expr().getText());
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
        System.out.println("État de la pile des scopes après la déclaration :");
        for (int i = typeScopes.size() - 1; i >= 0; i--) {
            System.out.println("Scope " + i + " : " + typeScopes.get(i));
        }

        return declaredType;
    }

    @Override
    public Type visitPrint(grammarTCLParser.PrintContext ctx) { // vérifié que la variable a été déclaré (donc parcourir le scope)
        System.out.println("VisitPrint");
        String variableName = ctx.VAR().getText();
        System.out.println("variableName : " + variableName);
        if(existsInAllScopes(variableName)==null){
            throw new UnsupportedOperationException("Variable '" + variableName + "' has not been declared.");
        };
        return null;
    }

    @Override
    public Type visitAssignment(grammarTCLParser.AssignmentContext ctx) {
        System.out.println("Visite assignement");

        String variableName = ctx.VAR().getText();
        ParseTree variable = ctx.getChild(0);
        String getChildTest = ctx.getChild(0).getText();

        System.out.println("Test get child text : " + getChildTest);

        // Recherche de la variable dans tous les scopes
        Map.Entry<UnknownType, Type> foundEntry = existsInAllScopes(variableName);

        // Si la variable n'est pas déclarée
        if (foundEntry == null) {
            throw new IllegalArgumentException("Variable non déclarée : " + variableName);
        }

        UnknownType declaredKey = foundEntry.getKey();
        Type declaredType = foundEntry.getValue();

        // Vérifie le type de l'expression assignée
        grammarTCLParser.ExprContext rightExpr = ctx.expr(0);
        Type rightType = visit(rightExpr);
        String rightVariableName = rightExpr.getText(); // Nom de la variable ou expression à droite

        System.out.println("Type de l'expression assignée : " + rightType);
        System.out.println("Membre gauche (clé UnknownType) : " + declaredKey);
        System.out.println("Membre gauche (type déclaré) : " + declaredType);
        System.out.println("Type de l'expression assignée a droite : " + rightType);


        // Utilisation de linkUnknownTypes pour établir un lien entre les deux UnknownType si applicable
        linkUnknownTypes(declaredKey, declaredType, rightVariableName, rightType);

        Map<UnknownType, Type> unification;

        if ((declaredType instanceof ArrayType && !(rightType instanceof ArrayType)) ||
                (!(declaredType instanceof ArrayType) && rightType instanceof ArrayType)) {
            throw new IllegalArgumentException("Incompatible types: Cannot assign an array to a primitive type or vice versa.");
        }

        unification = declaredKey.unify(rightType);

        // Si une unification est nécessaire
        if (!unification.isEmpty()) {
            System.out.println("HashMap d'unification : " + unification);
            System.out.println("declaredKey : " + declaredKey);
            // Applique les substitutions dans le scope de déclaration uniquement
            for (Map.Entry<UnknownType, Type> substitution : unification.entrySet()) {
                Type updatedType = substitution.getValue();
                propagateType(substitution.getKey(), updatedType); // Propage à toutes les variables liées
            }
        }

        System.out.println("Assignation réussie pour la variable " + variableName);
        return declaredType; // Retourne le type après assignation
    }



    @Override
    public Type visitBlock(grammarTCLParser.BlockContext ctx) {
        System.out.println("Visite d'un bloc : " + ctx.getText());

        // Ajouter un nouveau scope à la pile
        typeScopes.push(new HashMap<>());
        System.out.println("Entrée dans un nouveau bloc. Pile actuelle : " + typeScopes);

        try {
            // Visite des instructions à l'intérieur du bloc
            for (var instr : ctx.instr()) {
                System.out.println("Visite de l'instruction : " + instr.getText());
                visit(instr);
            }
        } finally {
            // Archiver le scope courant avant de le supprimer
            Map<UnknownType, Type> exitingScope = typeScopes.pop();
            archivedScopes.push(exitingScope);
            System.out.println("Sortie du bloc. Scope archivé : " + exitingScope);
            System.out.println("Pile actuelle après sortie : " + typeScopes);
        }

        return null; // Un bloc ne retourne pas de type spécifique
    }



    @Override
    public Type visitIf(grammarTCLParser.IfContext ctx) { // on peut inférer que le auto est un bool
        System.out.println("Visite d'un IF.");

        // Visite et vérifie le type de la condition
        System.out.println("Visite de la condition de l'IF : " + ctx.expr().getText());
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

        System.out.println("Condition de l'IF validée : " + conditionType);

        // Visite le bloc d'instructions du IF
        System.out.println("Visite du bloc THEN.");
        visit(ctx.instr(0)); // Premier bloc d'instructions après la condition

        // Visite le bloc ELSE s'il existe
        if (ctx.ELSE() != null) {
            System.out.println("Visite du bloc ELSE.");
            visit(ctx.instr(1)); // Deuxième bloc d'instructions (bloc ELSE)
        }

        System.out.println("Fin de la visite du IF.");
        return null; // Le IF ne retourne pas de type particulier
    }


    @Override
    public Type visitWhile(grammarTCLParser.WhileContext ctx) { //semblable au VisitIf
        System.out.println("visitWhile");
        Type conditionType = visit(ctx.expr());
        // La condition doit être de type BOOL
        if (!conditionType.equals(new PrimitiveType(Type.Base.BOOL))) {// il faut vérifier que l'expr est un bool
            throw new IllegalArgumentException("La condition du while doit être de type BOOL. Trouvé : " + conditionType);
        }
        visit(ctx.instr());// on visite l'instruction pour typer à l'intérieur de l'instruction
        return null; // le while ne retourne rien
    }

    @Override
    public Type visitFor(grammarTCLParser.ForContext ctx) { //selon la grammaire : 3 instr et 1 expr, on va ts les visiter et faire comme dans visitWhile et VisitIf
        System.out.println("visitFor"); //il faut s'assure que l'expr est un bool et visiter l'instruction pour typer
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

    @Override
    public Type visitReturn(grammarTCLParser.ReturnContext ctx) {
        System.out.println("Visite de l'instruction RETURN");
        System.out.println("Expression retournée : " + ctx.expr().getText());
        System.out.println("Table des types avant la visite de l'expression : " + types);

        // Récupère le type de l'expression retournée
        Type returnType = visit(ctx.expr());

        System.out.println("Type de retour déterminé : " + returnType);
        return returnType; // Retourne le type
    }


    @Override
    public Type visitCore_fct(grammarTCLParser.Core_fctContext ctx) {
        System.out.println("Visite de core_fct");

        // Parcourt et visite toutes les instructions
        for (var instr : ctx.instr()) {
            System.out.println("Visite d'une instruction : " + instr.getText());
            visit(instr); // Visite chaque instruction (y compris les déclarations)
        }

        // Visite l'expression de retour si elle existe
        if (ctx.RETURN() != null) {
            System.out.println("Visite de l'expression de retour : " + ctx.expr().getText());

            // Récupère le type de retour
            Type returnType = visit(ctx.expr());

            // Ajoute le typeScope actuel à l'archive
            if (!typeScopes.isEmpty()) {
                Map<UnknownType, Type> currentScope = new HashMap<>(typeScopes.peek());
                archivedScopes.push(currentScope);
                System.out.println("Scope actuel ajouté à l'archive au moment du RETURN : " + currentScope);
            } else {
                System.out.println("Aucun scope à archiver.");
            }

            return returnType; // Retourne le type de retour
        }

        return null; // Retourne null si aucune expression RETURN n'est présente
    }



    @Override
    public Type visitDecl_fct(grammarTCLParser.Decl_fctContext ctx) {
        System.out.println("Visite d'une déclaration de fonction.");

        // Récupération du type de retour et du nom de la fonction
        Type returnType = visit(ctx.type(0));
        String functionName = ctx.VAR(0).getText();
        System.out.println("Nom de la fonction : " + functionName);

        // Analyse des paramètres
        ArrayList<Type> parametres = new ArrayList<>();
        if (ctx.type().size() > 1) {
            for (int i = 1; i < ctx.type().size(); i++) {
                parametres.add(visit(ctx.type(i)));
            }
        }
        FunctionType functionType = new FunctionType(returnType, parametres);

        // Vérifie si la fonction existe déjà dans le scope actuel
        Map<UnknownType, Type> currentScope = typeScopes.peek();
        if (FunctionExistsInCurrentScope(functionName, currentScope)) {
            throw new UnsupportedOperationException("La fonction " + functionName + " est déjà déclarée.");
        }

        // Ajoute la fonction au scope global
        UnknownType functionKey = new UnknownType(ctx.VAR(0));
        addVariableToScope(functionKey, functionType, currentScope);

        // Création d'un scope temporaire pour les variables locales de la fonction
        typeScopes.push(new HashMap<>());
        System.out.println("Entrée dans le scope local de la fonction : " + functionName);

        // Visite du core de la fonction
        Type coreReturnType = visit(ctx.core_fct());

        // Vérification du type de retour
        if (!coreReturnType.equals(returnType)) {
            throw new IllegalArgumentException(
                    "Type de retour incompatible pour la fonction '" + functionName +
                            "'. Attendu : " + returnType + ", Trouvé : " + coreReturnType
            );
        }

        // Archiver le scope local de la fonction
        Map<UnknownType, Type> localScope = typeScopes.pop();
        System.out.println("Scope local archivé pour la fonction : " + functionName);

        return functionType;
    }



    @Override
    public Type visitMain(grammarTCLParser.MainContext ctx) {
        System.out.println("Visite de Main");

        // Parcourt les déclarations de fonctions
        for (var declFct : ctx.decl_fct()) {
            System.out.println("Visite de la déclaration de fonction : " + declFct.getText());
            visit(declFct);
        }

        // Visite le cœur de la fonction principale
        if (ctx.core_fct() != null) {
            System.out.println("Visite du corps de la fonction principale (core_fct)");
            visit(ctx.core_fct());
        }

        return null;
    }



}
