package br.com.sisgfin.core.validation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DocumentValidatorTest {

    // ── CPF ──────────────────────────────────────────────────────────────────

    @Test
    fun `CPF valido sem mascara`() {
        assertTrue(DocumentValidator.isValidCpf("52998224725"))
    }

    @Test
    fun `CPF valido com mascara`() {
        assertTrue(DocumentValidator.isValidCpf("529.982.247-25"))
    }

    @Test
    fun `CPF invalido digito verificador errado`() {
        assertFalse(DocumentValidator.isValidCpf("52998224724"))
    }

    @Test
    fun `CPF invalido todos digitos iguais`() {
        assertFalse(DocumentValidator.isValidCpf("11111111111"))
    }

    @Test
    fun `CPF invalido tamanho errado`() {
        assertFalse(DocumentValidator.isValidCpf("1234567890"))
    }

    // ── CNPJ ─────────────────────────────────────────────────────────────────

    @Test
    fun `CNPJ valido sem mascara`() {
        assertTrue(DocumentValidator.isValidCnpj("11222333000181"))
    }

    @Test
    fun `CNPJ valido com mascara`() {
        assertTrue(DocumentValidator.isValidCnpj("11.222.333/0001-81"))
    }

    @Test
    fun `CNPJ invalido digito verificador errado`() {
        assertFalse(DocumentValidator.isValidCnpj("11222333000182"))
    }

    @Test
    fun `CNPJ invalido todos digitos iguais`() {
        assertFalse(DocumentValidator.isValidCnpj("00000000000000"))
    }

    @Test
    fun `CNPJ invalido tamanho errado`() {
        assertFalse(DocumentValidator.isValidCnpj("1122233300018"))
    }

    // ── validate() ───────────────────────────────────────────────────────────

    @Test
    fun `validate nao lanca para CPF valido`() {
        assertDoesNotThrow { DocumentValidator.validate("529.982.247-25") }
    }

    @Test
    fun `validate nao lanca para CNPJ valido`() {
        assertDoesNotThrow { DocumentValidator.validate("11.222.333/0001-81") }
    }

    @Test
    fun `validate lanca para CPF invalido`() {
        val ex = assertThrows<IllegalArgumentException> { DocumentValidator.validate("111.111.111-11") }
        assertTrue(ex.message!!.contains("CPF"))
    }

    @Test
    fun `validate lanca para CNPJ invalido`() {
        val ex = assertThrows<IllegalArgumentException> { DocumentValidator.validate("00.000.000/0000-00") }
        assertTrue(ex.message!!.contains("CNPJ"))
    }

    @Test
    fun `validate lanca para tamanho errado`() {
        val ex = assertThrows<IllegalArgumentException> { DocumentValidator.validate("123") }
        assertTrue(ex.message!!.contains("inválido"))
    }

    // ── normalize() ──────────────────────────────────────────────────────────

    @Test
    fun `normalize remove mascara CPF`() {
        assertEquals("52998224725", DocumentValidator.normalize("529.982.247-25"))
    }

    @Test
    fun `normalize remove mascara CNPJ`() {
        assertEquals("11222333000181", DocumentValidator.normalize("11.222.333/0001-81"))
    }

    @Test
    fun `normalize ja sem mascara retorna igual`() {
        assertEquals("52998224725", DocumentValidator.normalize("52998224725"))
    }
}
