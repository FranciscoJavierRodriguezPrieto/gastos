-- Gastos fijos: el alquiler, el telefono, la cuota del gimnasio.
--
-- Una plantilla, no un gasto. Cada mes que se abre, la plantilla genera un gasto real en
-- `expense` y a partir de ahi ese gasto es uno normal: se edita, se borra y cuenta en los
-- totales como cualquier otro.
--
-- De ahi salen las dos propiedades que se buscaban. Corregir la luz de enero no toca la de
-- febrero, porque lo que se edita es el gasto generado y no la plantilla. Y subir el
-- alquiler no reescribe los meses ya pagados, porque cada mes guarda ya su propio importe:
-- el historico sale gratis, sin versionar nada.

create table fixed_expense (
    id           uuid          primary key,
    household_id uuid          not null,
    created_by   uuid          not null,
    description  varchar(140)  not null,
    amount       numeric(15,2) not null,
    category     varchar(20)   not null,
    -- Dia de cargo, de 1 a 28. No 31: un cargo el 31 no existe en febrero, y decidir en
    -- el momento de generar si cae el ultimo dia del mes o el primero del siguiente es
    -- justo la clase de regla que luego nadie recuerda al mirar un total que no cuadra.
    day_of_month integer       not null check (day_of_month between 1 and 28),
    account_id   uuid,
    -- Vigencia en meses, no en fechas: un gasto fijo pertenece a un mes entero.
    -- Se guardan como el dia 1 del mes correspondiente.
    start_month  date          not null,
    -- Null = sigue vigente. Al darlo de baja se guarda el primer mes que YA NO genera.
    end_month    date
);

create index idx_fixed_expense_household on fixed_expense (household_id);

-- Que plantilla genero ya su gasto en que mes.
--
-- Es lo que hace que la expansion sea idempotente y, sobre todo, que borrar sea
-- definitivo: si alguien borra el gasto generado de un mes, la marca sigue aqui y no
-- vuelve a aparecer al recargar. Sin esta tabla, borrar seria un deseo que se deshace
-- solo.
-- "applied_month" y no "month": MONTH es palabra reservada en SQL estandar y obligaria a
-- entrecomillarla en cada consulta, igual que pasa con USER en la tabla app_user.
create table fixed_expense_application (
    fixed_expense_id uuid not null references fixed_expense (id) on delete cascade,
    -- Dia 1 del mes generado.
    applied_month    date not null,
    primary key (fixed_expense_id, applied_month)
);

-- De que plantilla salio un gasto. Null para los que se registran a mano, que son la
-- mayoria.
alter table expense add column fixed_expense_id uuid;

-- ON DELETE SET NULL y no CASCADE, a proposito: borrar la plantilla del gimnasio no puede
-- borrar las cuotas que de verdad se pagaron. Dejar de generar y borrar el historico son
-- cosas distintas; confundirlas haria que darse de baja en junio cambiase el total de
-- marzo.
alter table expense
    add constraint fk_expense_fixed_expense
    foreign key (fixed_expense_id) references fixed_expense (id) on delete set null;

-- Para localizar en un mes los gastos que vienen de plantilla.
create index idx_expense_fixed_expense on expense (fixed_expense_id);
