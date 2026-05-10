// copied from components-react
import React from "react";

//import { shortId } from "../utils/misc";
//import { styleFromCssDecl } from "../../utils/tinyparsers";
//function addStyle(styleString) {
//	const style = document.createElement('style');
//	style.textContent = styleString;
//	document.head.append(style);
//}
let TSH = (s:S) => {for(var h=9,i=s.length;i;)h=Math.imul(h^s.charCodeAt(--i),9**9);return h^h>>>9}
// https://gist.github.com/LeverOne/1308368
export let uuid2 = () => {for(var a,b=a='';a++<36;b+=a*51&52?(a^15?8^Math.random()*(a^20?16:4):4).toString(16):'-');return b}
let shortId = () => Math.abs(TSH(uuid2())).toString(36);
let styles = {};
window["styles"] = styles;

export default class Style extends React.Component<{ css?, inline?, children }> {
	id = "x-"+shortId();
	ref = React.createRef<HTMLStyleElement>();
	target;
	applyCss;
	oldCss;
	direct;
	css;
	mo: MutationObserver;

	render() {
		let { id, oldCss } = this;
		let { css, inline, children } = this.props;
		css = css || ""+children;
		if (css!=oldCss) {
			this.oldCss = css;
			let hasStyle = css => /[a-z-]+\s*:\s*[^;]+;?/.test(css.replace(/[^{}]*\{|\}/g,""));
			if (!hasStyle(css)) {
				css = "";
				styles[id] = { inline, direct:"", others:"", css };
				this.direct = "";
				this.applyCss = false;
			}
			let clean = css => {
				css = css.trim().split(/([{}])/).reduce((r,s) => r += s.length<=1 ? s :
					s.replace(/[\r\n]+/g,"").replace(/\s{2,}/g," "),"");
				return css.replace(/\s*\{\s*/g," { ").replace(/\s*\}\s*/g," }\n");
			};
			let before = css;
			css = css && css.replace(/\/\*[\s\S]*?\*\/|([^\\:]|^)\/\/.*$/gm,"$1");
			css = css && css.replace(/&(?=[\.\s#:])/g,"."+id);
			if (css!=before) {
				let sub = css.match(/[^\r\n;]*{.*/s);
				let direct = sub ? clean(css.slice(0,-sub[0].length)) : "";
				let others = sub ? clean(sub[0]) : "";
				css = inline ? others : (direct ? "."+id+" { "+direct+" }\n" : "") + others;
				styles[id] = { inline, direct, others, css };
//				styles[id].style = styleFromCssDecl(direct);
				this.direct = direct;
				this.applyCss = true;
			} else {
				styles[id] = { inline, direct:"", others:"", css };
				this.direct = "";
				this.applyCss = false;
			}
			this.css = css;
			this.apply();
		}
		return <style ref={this.ref}>{this.css}</style>
	}

	apply() {
		let { id, css, direct, target, applyCss } = this;
		let { inline } = this.props;
		if (!target || !applyCss) return;
		target.classList.toggle(id,!inline || css);
		if (!inline || css) {
			this.mo = new MutationObserver(mutations => {
				mutations.forEach(mutation => {
					target.classList.toggle(id,!inline || css);
				});
			});
			this.mo.observe(target,{ attributeFilter: ["class"] });
//			let mo = new MutationObserver(function(mutations) { mutations.forEach(m => {
//				if (m.type!="attributes") return;
////				if (m.attributeName!="class") return;
////				requestAnimationFrame(() => {
////					ignore = true;
////					target.classList.toggle(id,!inline || css);
////					ignore = false;
////				});
//			})});
//			mo.observe(target,{ attributes: true });
		}
		if (inline) {
			let compact = direct.replace(/;\s+(\w)/g,";$1").replace(/(\w):\s+/g,"$1:");
			let cssText = target.style.cssText||"";
			cssText = cssText.replace(/.*---:\s*---;\s*/,"");
			target.style.cssText = compact+"---:---; " + cssText;
		}
	}

	componentDidMount() {
		let place = this.ref.current;
		let parent = place?.parentElement;
		this.target = place && (place==parent?.firstElementChild ? parent : place.previousElementSibling) as HTMLElement;
		this.apply();
	}
	componentWillUnmount() {
		this.mo?.disconnect();
	}
}
