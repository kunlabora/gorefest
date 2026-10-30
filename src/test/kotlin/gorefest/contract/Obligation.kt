package gorefest.contract

/**
 * Marks a test as proof of one or more obligations in `docs/contract/`.
 * `ContractCoverageTest` fails when an obligation has no proof, or a test names one that doesn't exist.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Obligation(vararg val ids: String)
