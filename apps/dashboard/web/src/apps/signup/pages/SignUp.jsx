import SignUpCard from "../components/SignUpCard"
import ProductPreview from "../components/ProductPreview"

const SignUp = () => {

  return (
    <div style={{display: "grid", gridTemplateColumns: "1fr 1.33fr", height: "100vh"}}>
      <SignUpCard/>
      <ProductPreview/>
    </div>   
  )
}

export default SignUp