# 中国企业薪酬合规核算实现说明

基准日：2026-07-18
适用范围：中国境内企业劳动合同员工的工资薪金核算
文档性质：产品与技术实现说明，不替代法律、税务或社保专业意见

## 本次切换

旧版“当月应税金额乘固定税率”已下线。新规则包必须声明 `tax.mode=cumulative_withholding`，否则不能发布或进入核算。历史旧模板保留为迁移证据，但迁移脚本会将其停用。

每个工资批次现在可以保存实际发放日、税务年度、税款所属月、扣缴义务人、政策包版本、输入冻结时间、结果摘要和调整来源。工资行保存个税解释快照；累计个税草稿台账和逐步计算轨迹与核算事务保持同一事务边界。

## 已落地能力

- 十进制定点累计预扣：累计收入、免税收入、减除费用、专项扣除、七项专项附加扣除、其他依法扣除、减免税额和已预扣税额均单独建模。
- 七项专项附加扣除与个人养老金申报记录：声明、有效期、分配比例、凭证元数据和审核状态分开保存；大病医疗按年度汇算场景处理，住房贷款利息和住房租金互斥。
- 政策版本：政策类型、地区、人员范围、生效区间、官方依据、参数快照、复核人和发布人均可追溯。政策复核人与发布人必须分离。
- 五险一金基础：按统筹地区、征缴主体、险种、人员类别、行业风险和有效期保存基数上下限、单位/个人费率和舍入规则。
- 多地参保：同一员工同一险种有效期重叠会被阻止，跨地区转入、转出、封存和清退应通过新的关系事件处理。
- 计算解释：工资项目、累计个税、净额结算都可以落入 `payroll_calculation_trace`，批次重算产生新的工资行版本。
- 前端合规工作台：政策状态、七项扣除、税率档位、累计预扣试算、奖金试算、员工年度台账和参保关系入口统一到薪酬模块。

## 关键接口

- `POST /api/payroll/compliance/tax/preview`
- `POST /api/payroll/compliance/tax/annual-bonus-preview`
- `POST /api/payroll/compliance/contributions/preview`
- `GET /api/payroll/compliance/policies`
- `POST /api/payroll/compliance/policies/{id}/review`
- `POST /api/payroll/compliance/policies/{id}/publish`
- `GET/POST /api/payroll/compliance/deductions`
- `GET /api/payroll/compliance/tax-ledger`
- `GET/POST /api/payroll/compliance/enrollments`
- `GET /api/payroll/compliance/traces/{lineId}`

## 数据库迁移

生产环境默认启用 `MIGRATION_RUNNER_ENABLED=true`，应用启动会幂等创建合规基础表、注册合规菜单/API 权限并停用旧固定税率模板。完整 SQL 迁移文件为：

`src/main/resources/sql/migrations/2026-07-18__payroll_compliance_foundation.sql`

首次部署前应先备份数据库，并由财务/税务负责人确认首批法人、扣缴义务人、参保主体、地区和政策版本。政策包中的地方五险一金参数不能用全国默认值替代。

## 尚需专业复核的范围

非居民、外籍人员、劳务报酬、股权激励、解除劳动关系一次性补偿、综合工时/不定时工时、地方病假工资与最低工资、各地申报文件和官方回执适配，均应作为独立政策包和地区适配器验收，不能套用普通居民工资薪金累计预扣公式。

## 依据

- 国家税务总局公告2018年第61号及累计预扣预缴官方解读
- 国发〔2023〕13号及国家税务总局公告2023年第14号
- 财政部、税务总局公告2023年第30号
- 财政部、税务总局公告2024年第21号
- 《社会保险经办条例》
- 《住房公积金管理条例》
