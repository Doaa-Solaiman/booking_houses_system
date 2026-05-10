import React, { createContext, useContext} from "react";

type Drafts = {
[section: string]: any;
};

type FormDraftContextType = {
	drafts: Drafts;
	saveDraft: (section: string, data: any) => void;
	getDraft: (section: string) => any;
	clearDraft: (section: string | string[]) => void;
};

const FormDraftContext = React.createContext<FormDraftContextType | undefined>(undefined);

export const FormDraftProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
	const saveDraft = (section: string, data: any) => {
		const saved = localStorage.getItem("formDrafts");
		try {
			let drafts = JSON.parse(saved);
			const updated = { ...drafts, [section]: data };
			localStorage.setItem("formDrafts", JSON.stringify(updated));
		} catch (e) {}
	};
	
	
	const getDraft = (section: string) => {
		const saved = localStorage.getItem("formDrafts");
		try {
			let drafts = JSON.parse(saved);
			return drafts[section];
		} catch (e) {
			return null;
		}
	}
	
	const clearDraft = (section: string | string[]) => {
		saveDraft(section,undefined);
	};
	
	return (
		<FormDraftContext.Provider value={{ saveDraft, getDraft, clearDraft }}>
		{children}
		</FormDraftContext.Provider>
	);
};

export const useFormDraft = (): FormDraftContextType => {
const ctx = React.useContext(FormDraftContext);
if (!ctx) {
	throw new Error("the useFormDraft must be used within a FormDraftProvider");
}
return ctx;
};
