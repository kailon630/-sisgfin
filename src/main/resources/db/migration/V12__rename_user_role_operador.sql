-- V12: renomeia o perfil USER para OPERADOR (RN-12)
-- Atualiza registros existentes e ajusta o valor default da coluna.
UPDATE users SET role = 'OPERADOR' WHERE role = 'USER';
