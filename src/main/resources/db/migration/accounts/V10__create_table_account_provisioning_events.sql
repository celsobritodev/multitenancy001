

-- Regra: TaxId único por país + tipo + número, respeitando soft-delete
CREATE UNIQUE INDEX uk_accounts_taxid_country_type_number_not_deleted
    ON public.accounts (tax_country_code, tax_id_type, tax_id_number)
    WHERE deleted = false;

-- (Opcional, mas recomendado) evitar duplicidade de login_email também, se ainda não existir:
-- CREATE UNIQUE INDEX uk_accounts_login_email_not_deleted
--     ON public.accounts (login_email)
--     WHERE deleted = false;
