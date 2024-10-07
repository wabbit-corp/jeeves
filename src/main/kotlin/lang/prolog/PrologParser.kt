package lang.prolog

//object PrologParser {
//    fun parse(s: String): Rule {
//        val input = CharInput.of3(s)
//        return parse(input)
//    }
//
//    fun parse(input: CharInput<Span3>): Rule {
//        skipWS(input)
//        return parseRule(input)
//    }
//
//    // daughter_of(X,Y) :- father_of(Y,X), female(X).
//    fun parseRule(input: CharInput<Span3>): Rule {
//        val head = parseCompound(input)
//
//        skipWS(input)
//
//        if (input.current == '.') {
//            input.advance()
//            return Rule(head, Compound("true", emptyList()))
//        }
//
//        if (input.current != ':') throw IllegalArgumentException("Expected ':'")
//        input.advance()
//        if (input.current != '-') throw IllegalArgumentException("Expected '-'")
//        input.advance()
//        skipWS(input)
//
//        val bodyTerms = mutableListOf<Compound>()
//        while (input.current != '.' && input.current != CharInput.EOB) {
//            bodyTerms.add(parseCompound(input))
//            skipWS(input)
//            if (input.current == ',') {
//                input.advance()
//            }
//            skipWS(input)
//        }
//
//        if (input.current == '.') {
//            input.advance()
//        }
//        if (bodyTerms.size == 0) {
//            return Rule(head, Compound("true", emptyList()))
//        }
//        if (bodyTerms.size == 1) {
//            return Rule(head, bodyTerms[0])
//        }
//        return Rule(head, Compound(",", bodyTerms))
//    }
//
//    // daughter_of(X,Y)
//    fun parseCompound(input: CharInput<Span3>): Compound {
//        require(input.current.isLetter()) { "Expected letter, got ${input.current}" }
//        val functor = parseAtom(input)
//        val args = mutableListOf<Term>()
//        if (input.current == '(') {
//            input.advance()
//            while (input.current != ')' && input.current != CharInput.EOB) {
//                args.add(parseTerm(input))
//                skipWS(input)
//                if (input.current == ',') {
//                    input.advance()
//                }
//            }
//            if (input.current == ')') {
//                input.advance()
//            }
//        }
//        return Compound(functor, args)
//    }
//
//    fun parseTerm(input: CharInput<Span3>): Term {
//        skipWS(input)
//        if (!input.current.isLetter()) {
//            throw IllegalArgumentException("Expected letter")
//        }
//        if (input.current.isUpperCase()) {
//            val atom = parseAtom(input)
//            return Var(atom)
//        } else {
//            val atom = parseAtom(input)
//            println("atom: $atom")
//            skipWS(input)
//            if (input.current == '(') {
//                input.advance()
//                return parseCompound(input)
//            } else {
//                return Compound(atom, emptyList())
//            }
//        }
//    }
//
//    fun parseAtom(input: CharInput<Span3>): String {
//        val sb = StringBuilder()
//        require(input.current.isLetterOrDigit()) { "Expected letter or digit" }
//        while (input.current.isLetterOrDigit()) {
//            sb.append(input.current)
//            input.advance()
//        }
//        return sb.toString()
//    }
//
//    fun skipWS(input: CharInput<Span3>): Unit {
//        while (input.current.isWhitespace()) {
//            input.advance()
//        }
//    }
//}
